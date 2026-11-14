package com.aibusinessassistant.common.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatEvent;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

// A scripted model asks for a tool that throws. GeminiAiService and OllamaAiService declare no tool error handler,
// so they use the same project-wide RethrowingToolExecutionErrorHandler as this test service. No model is called.
@QuarkusTest
class ToolFailureTest {

    static final String QUERY = "Which products are low stock?";

    @Inject
    ToolFailureAiService aiService;

    @BeforeEach
    void clearRequests() {
        ScriptedChatModel.REQUESTS.clear();
    }

    @Test
    void aFailingToolFailsTheRequestAndItsMessageIsNotSentToTheModel() {
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> aiService.chat(QUERY));

        assertEquals(FailingTools.MESSAGE, failure.getMessage());
        assertNoToolResultReachedTheModel();
    }

    @Test
    void aFailingToolFailsTheStreamAndItsMessageIsNotSentToTheModel() {
        AssertSubscriber<ChatEvent> subscriber = aiService.chatStream(QUERY).subscribe()
                .withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));

        subscriber.awaitFailure().assertFailedWith(IllegalStateException.class, FailingTools.MESSAGE);
        assertNoToolResultReachedTheModel();
    }

    // The tool result would arrive in a second request; only the request that asked for the tool exists.
    private static void assertNoToolResultReachedTheModel() {
        assertEquals(1, ScriptedChatModel.REQUESTS.size());
        assertFalse(ScriptedChatModel.REQUESTS.getFirst().messages().toString().contains(FailingTools.MESSAGE));
    }

    @ApplicationScoped
    @RegisterAiService(
            chatLanguageModelSupplier = ScriptedChatModel.ModelSupplier.class,
            streamingChatLanguageModelSupplier = ScriptedChatModel.StreamingModelSupplier.class,
            chatMemoryProviderSupplier = ToolFailureTest.InMemoryChatMemory.class,
            tools = FailingTools.class)
    public interface ToolFailureAiService {

        @UserMessage("{query}")
        String chat(String query);

        @UserMessage("{query}")
        Multi<ChatEvent> chatStream(String query);
    }

    // Tools require chat memory; an in-memory window avoids the conversation rows the PostgreSQL store needs.
    public static class InMemoryChatMemory implements Supplier<ChatMemoryProvider> {

        @Override
        public ChatMemoryProvider get() {
            return memoryId -> MessageWindowChatMemory.withMaxMessages(10);
        }
    }

    @ApplicationScoped
    public static class FailingTools {

        static final String MESSAGE = "Inventory database unavailable";

        @Tool("Returns products whose stock quantity is at or below their minimum stock level.")
        public String getLowStockProducts() {
            throw new IllegalStateException(MESSAGE);
        }
    }

    // Asks for the tool on the first request and answers on any later one; records every request.
    public static class ScriptedChatModel implements ChatModel {

        static final List<ChatRequest> REQUESTS = new CopyOnWriteArrayList<>();
        static final ScriptedChatModel INSTANCE = new ScriptedChatModel();

        @Override
        public ChatResponse doChat(ChatRequest request) {
            REQUESTS.add(request);
            AiMessage message = REQUESTS.size() == 1
                    ? AiMessage.from(ToolExecutionRequest.builder().id("1").name("getLowStockProducts")
                            .arguments("{}").build())
                    : AiMessage.from("No products are low stock.");
            return ChatResponse.builder().aiMessage(message).build();
        }

        public static class ModelSupplier implements Supplier<ChatModel> {

            @Override
            public ChatModel get() {
                return INSTANCE;
            }
        }

        // The same script, delivered as one complete streamed response.
        public static class StreamingModelSupplier implements Supplier<StreamingChatModel> {

            @Override
            public StreamingChatModel get() {
                return new StreamingChatModel() {
                    @Override
                    public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                        handler.onCompleteResponse(INSTANCE.doChat(request));
                    }
                };
            }
        }
    }
}
