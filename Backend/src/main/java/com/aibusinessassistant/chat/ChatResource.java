package com.aibusinessassistant.chat;

import java.util.UUID;

import com.aibusinessassistant.chat.ai.BusinessAnalysisService;
import com.aibusinessassistant.chat.dto.BusinessAnalysisDTO;
import com.aibusinessassistant.chat.dto.ChatRequestDTO;
import com.aibusinessassistant.chat.history.ConversationHistoryService;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Path("/api/chat")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
@Slf4j
public class ChatResource {

    private final BusinessAnalysisService businessAnalysisService;
    private final ConversationHistoryService conversationHistoryService;
    private final ChatMemoryStore chatMemoryStore;

    @POST
    public BusinessAnalysisDTO chat(ChatRequestDTO request) {
        if (request == null || request.conversationId() == null || request.conversationId().isBlank()) {
            throw new BadRequestException("conversationId is required");
        }
        String query = request.query();
        UUID conversationId = conversationId(request.conversationId());
        conversationHistoryService.findOrCreateConversation(conversationId);
        log.info("AI request received: business-analysis (conversationId={}, queryLength={}, memoryMessagesBefore={})",
                conversationId, queryLength(query), chatMemoryStore.getMessages(conversationId).size());
        long startedAt = System.nanoTime();

        try {
            BusinessAnalysisDTO response = businessAnalysisService.chat(conversationId, query);
            conversationHistoryService.recordSuccessfulExchange(conversationId, query, response);
            log.info("AI request completed: business-analysis (conversationId={}, durationMs={}, memoryMessagesAfter={})",
                    conversationId, elapsedMilliseconds(startedAt), chatMemoryStore.getMessages(conversationId).size());
            return response;
        } catch (RuntimeException exception) {
            log.error("AI request failed: business-analysis (durationMs={})", elapsedMilliseconds(startedAt), exception);
            throw exception;
        }
    }

    private static Integer queryLength(String query) {
        return query == null ? null : query.length();
    }

    private static UUID conversationId(String rawConversationId) {
        try {
            return UUID.fromString(rawConversationId);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("conversationId must be a UUID", exception);
        }
    }

    private static long elapsedMilliseconds(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
