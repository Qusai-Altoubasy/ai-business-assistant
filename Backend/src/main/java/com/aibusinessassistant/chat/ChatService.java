package com.aibusinessassistant.chat;

import java.util.UUID;

import com.aibusinessassistant.chat.ai.BusinessAnalysisService;
import com.aibusinessassistant.chat.dto.BusinessAnalysisDTO;
import com.aibusinessassistant.chat.dto.ChatStreamEventDTO;
import com.aibusinessassistant.chat.dto.StreamEventType;
import com.aibusinessassistant.chat.history.ConversationHistoryService;

import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final BusinessAnalysisService businessAnalysisService;
    private final ConversationHistoryService conversationHistoryService;
    private final ChatMemoryStore chatMemoryStore;

    public BusinessAnalysisDTO chat(UUID conversationId, String query) {
        log.info("AI request received: business-analysis (conversationId={}, queryLength={}, memoryMessagesBefore={})",
                conversationId, queryLength(query), chatMemoryStore.getMessages(conversationId).size());
        long startedAtNanos = System.nanoTime();

        try {
            BusinessAnalysisDTO response = businessAnalysisService.chat(conversationId, query);
            conversationHistoryService.recordSuccessfulExchange(conversationId, query, response);
            log.info("AI request completed: business-analysis (conversationId={}, durationMs={}, memoryMessagesAfter={})",
                    conversationId, elapsedMilliseconds(startedAtNanos), chatMemoryStore.getMessages(conversationId).size());
            return response;
        } catch (RuntimeException exception) {
            log.error("AI request failed: business-analysis (durationMs={})", elapsedMilliseconds(startedAtNanos), exception);
            throw exception;
        }
    }

    public Multi<ChatStreamEventDTO> chatStream(UUID conversationId, String query) {
        log.info("AI streaming request received: business-analysis (conversationId={})", conversationId);
        long startedAtNanos = System.nanoTime();

        // Accumulate only for persistent history; each chunk is sent immediately to the client.
        return Multi.createFrom().deferred(() -> {
            StringBuilder accumulatedResponse = new StringBuilder();
            return businessAnalysisService.chatStream(conversationId, query)
                    // Completion can arrive on the event loop; history writes need a worker.
                    .emitOn(Infrastructure.getDefaultWorkerPool())
                    .filter(ChatService::isClientVisibleEvent)
                    .invoke(event -> appendPartialResponse(event, accumulatedResponse))
                    .map(ChatService::toStreamEvent)
                    .onCompletion().invoke(() -> completeStreamingRequest(
                            conversationId, query, accumulatedResponse, startedAtNanos))
                    .onCompletion().continueWith(doneEvent());
        }).onFailure().invoke(exception -> logStreamingFailure(conversationId, startedAtNanos, exception))
                .onFailure().recoverWithItem(errorEvent());
    }

    private static boolean isClientVisibleEvent(ChatEvent event) {
        return event instanceof ChatEvent.PartialResponseEvent
                || event instanceof ChatEvent.BeforeToolExecutionEvent
                || event instanceof ChatEvent.ToolExecutedEvent;
    }

    private static void appendPartialResponse(ChatEvent event, StringBuilder accumulatedResponse) {
        if (event instanceof ChatEvent.PartialResponseEvent partial) {
            accumulatedResponse.append(partial.getChunk());
        }
    }

    private static ChatStreamEventDTO toStreamEvent(ChatEvent event) {
        if (event instanceof ChatEvent.PartialResponseEvent partial) {
            return new ChatStreamEventDTO(StreamEventType.CHUNK, partial.getChunk());
        }
        if (event instanceof ChatEvent.BeforeToolExecutionEvent before) {
            return new ChatStreamEventDTO(StreamEventType.TOOL_STARTED, before.getRequest().name());
        }
        ChatEvent.ToolExecutedEvent completed = (ChatEvent.ToolExecutedEvent) event;
        return new ChatStreamEventDTO(StreamEventType.TOOL_COMPLETED,
                completed.getExecution().request().name());
    }

    private void completeStreamingRequest(
            UUID conversationId, String query, StringBuilder accumulatedResponse, long startedAtNanos) {
        conversationHistoryService.recordSuccessfulTextExchange(
                conversationId, query, accumulatedResponse.toString());
        log.info("AI streaming request completed: business-analysis (conversationId={}, durationMs={})",
                conversationId, elapsedMilliseconds(startedAtNanos));
    }

    private static void logStreamingFailure(UUID conversationId, long startedAtNanos, Throwable exception) {
        log.error(
                "AI streaming request failed: business-analysis (conversationId={}, durationMs={})",
                conversationId, elapsedMilliseconds(startedAtNanos), exception);
    }

    private static ChatStreamEventDTO doneEvent() {
        return new ChatStreamEventDTO(StreamEventType.DONE, null);
    }

    private static ChatStreamEventDTO errorEvent() {
        return new ChatStreamEventDTO(StreamEventType.ERROR, "Unable to complete the streaming request.");
    }

    private static Integer queryLength(String query) {
        return query == null ? null : query.length();
    }

    private static long elapsedMilliseconds(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }
}
