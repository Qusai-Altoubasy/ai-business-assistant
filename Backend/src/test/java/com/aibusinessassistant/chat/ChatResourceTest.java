package com.aibusinessassistant.chat;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.aibusinessassistant.chat.ai.BusinessAnalysisService;
import com.aibusinessassistant.chat.dto.BusinessAnalysisDTO;
import com.aibusinessassistant.chat.dto.ChatRequestDTO;
import com.aibusinessassistant.chat.dto.ChatStreamEventDTO;
import com.aibusinessassistant.chat.dto.StreamEventType;
import com.aibusinessassistant.chat.history.ConversationMessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.tool.ToolExecution;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatEvent;
import io.smallrye.mutiny.Multi;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

@QuarkusTest
class ChatResourceTest {

    private static final String QUERY = "What can you help me with?";
    static final String AI_RESPONSE = "I can help with your business questions.";
    static final List<String> INSIGHTS = List.of("Inventory and sales data are available.");
    static final List<String> RECOMMENDATIONS = List.of("Choose a product or sales period to review.");

    @Inject
    ConversationMessageRepository messages;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ChatService chatService;

    @Test
    void normalChatServiceRethrowsAiFailureWithoutRecordingSuccessfulHistory() {
        UUID conversationId = UUID.randomUUID();
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> chatService.chat(conversationId, "provider-failure"));

        assertEquals("Provider internal credentials/details", failure.getMessage());
        assertEquals(conversationId, TestBusinessAnalysisService.lastConversationId);
        assertEquals("provider-failure", TestBusinessAnalysisService.lastQuery);
        assertEquals(0, messages.listByConversationId(conversationId).size());
    }

    @Test
    void streamingFailureEmitsOnlyASafeErrorAndDoesNotRecordASuccessfulExchange() throws JsonProcessingException {
        UUID conversationId = UUID.randomUUID();
        var events = streamEvents(conversationId, "provider-failure");

        assertEquals(List.of(safeError()), events);
        assertEquals(0, messages.listByConversationId(conversationId).size());
    }

    @Test
    void streamingFailureAfterAChunkDoesNotEmitDoneOrSavePartialHistory() throws JsonProcessingException {
        UUID conversationId = UUID.randomUUID();
        assertEquals(List.of(new ChatStreamEventDTO(StreamEventType.CHUNK, "Partial answer"), safeError()),
                streamEvents(conversationId, "partial-failure"));
        assertEquals(0, messages.listByConversationId(conversationId).size());
    }

    @Test
    void synchronousAiFailureAlsoBecomesASafeError() throws JsonProcessingException {
        UUID conversationId = UUID.randomUUID();
        assertEquals(List.of(safeError()), streamEvents(conversationId, "synchronous-failure"));
        assertEquals(0, messages.listByConversationId(conversationId).size());
    }

    @Test
    void historyFailureEmitsErrorInsteadOfDoneAndRollsBackTheExchange() throws JsonProcessingException {
        UUID conversationId = UUID.randomUUID();
        // The existing query contract permits null; history's NOT NULL content rejects it.
        var events = streamEvents(conversationId, null);
        assertEquals(safeError(), events.getLast());
        assertFalse(events.stream().anyMatch(event -> event.type() == StreamEventType.DONE));
        assertEquals(0, messages.listByConversationId(conversationId).size());
    }

    @Test
    void streamingEndpointReturnsJsonChunksAndOneDoneAfterPersistingTheExchange() throws JsonProcessingException {
        UUID conversationId = UUID.randomUUID();
        assertEquals(List.of(new ChatStreamEventDTO(StreamEventType.CHUNK, "I can help "),
                new ChatStreamEventDTO(StreamEventType.CHUNK, "with your business questions."),
                new ChatStreamEventDTO(StreamEventType.DONE, null)), streamEvents(conversationId, QUERY));

        assertEquals(QUERY, TestBusinessAnalysisService.lastQuery);
        assertEquals(conversationId, TestBusinessAnalysisService.lastConversationId);
        var history = messages.listByConversationId(conversationId);
        assertEquals(2, history.size());
        assertEquals(QUERY, history.getFirst().getContent());
        assertEquals(AI_RESPONSE, history.getLast().getContent());
    }

    @Test
    void toolEventsContainOnlyNamesAndAreExcludedFromHistory() throws JsonProcessingException {
        UUID conversationId = UUID.randomUUID();
        assertEquals(List.of(new ChatStreamEventDTO(StreamEventType.TOOL_STARTED, "getLowStockProducts"),
                new ChatStreamEventDTO(StreamEventType.TOOL_COMPLETED, "getLowStockProducts"),
                new ChatStreamEventDTO(StreamEventType.CHUNK, "Three products are low stock."),
                new ChatStreamEventDTO(StreamEventType.DONE, null)), streamEvents(conversationId, "tool-request"));
        var history = messages.listByConversationId(conversationId);
        assertEquals(2, history.size());
        assertEquals("tool-request", history.getFirst().getContent());
        assertEquals("Three products are low stock.", history.getLast().getContent());
    }

    private List<ChatStreamEventDTO> streamEvents(UUID conversationId, String query) throws JsonProcessingException {
        String body = given()
                .contentType(ContentType.JSON)
                .accept("text/event-stream")
                .body(new ChatRequestDTO(conversationId.toString(), query))
                .when().post("/api/chat/stream")
                .then().statusCode(200).contentType("text/event-stream")
                .extract().asString();
        List<ChatStreamEventDTO> events = new ArrayList<>();
        for (String line : body.lines().toList()) {
            if (line.startsWith("data:")) {
                events.add(objectMapper.readValue(line.substring(5), ChatStreamEventDTO.class));
            }
        }
        return events;
    }

    private static ChatStreamEventDTO safeError() {
        return new ChatStreamEventDTO(StreamEventType.ERROR, "Unable to complete the streaming request.");
    }

    @Test
    void streamingEndpointRejectsMissingBlankAndInvalidConversationIds() {
        for (String body : List.of("null", "{\"query\":\"test\"}",
                "{\"conversationId\":\" \"}", "{\"conversationId\":\"not-a-uuid\"}")) {
            given()
                    .contentType(ContentType.JSON)
                    .accept("text/event-stream")
                    .body(body)
                    .when().post("/api/chat/stream")
                    .then().statusCode(400);
        }
    }

    @Test
    void chatEndpointReturnsStructuredAnalysisAsJson() throws JsonProcessingException {
        UUID conversationId = UUID.randomUUID();
        given()
                .contentType(ContentType.JSON)
                .body(new ChatRequestDTO(conversationId.toString(), QUERY))
                .when().post("/api/chat")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("summary", is(AI_RESPONSE))
                .body("insights", is(INSIGHTS))
                .body("recommendations", is(RECOMMENDATIONS));

        assertEquals(QUERY, TestBusinessAnalysisService.lastQuery);
        assertEquals(conversationId, TestBusinessAnalysisService.lastConversationId);
        var history = messages.listByConversationId(conversationId);
        assertEquals(2, history.size());
        assertEquals(QUERY, history.getFirst().getContent());
        assertEquals(new BusinessAnalysisDTO(AI_RESPONSE, INSIGHTS, RECOMMENDATIONS),
                objectMapper.readValue(history.getLast().getContent(), BusinessAnalysisDTO.class));
    }

    @Test
    void chatEndpointRequiresConversationId() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"query\":\"" + QUERY + "\"}")
                .when().post("/api/chat")
                .then().statusCode(400);
    }

    @Test
    void chatEndpointRequiresUuidConversationId() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"conversationId\":\"conversation-123\",\"query\":\"" + QUERY + "\"}")
                .when().post("/api/chat")
                .then().statusCode(400);
    }
}

@Alternative
@Priority(1)
@ApplicationScoped
class TestBusinessAnalysisService implements BusinessAnalysisService {

    static volatile String lastQuery;
    static volatile UUID lastConversationId;

    @Override
    public Multi<ChatEvent> chatStream(UUID conversationId, String query) {
        lastConversationId = conversationId;
        lastQuery = query;
        if ("provider-failure".equals(query)) {
            return Multi.createFrom().failure(new IllegalStateException("Provider internal credentials/details"));
        }
        if ("synchronous-failure".equals(query)) {
            throw new IllegalStateException("Provider internal credentials/details");
        }
        if ("partial-failure".equals(query)) {
            return Multi.createBy().concatenating().streams(
                    Multi.createFrom().item(new ChatEvent.PartialResponseEvent("Partial answer")),
                    Multi.createFrom().failure(new IllegalStateException("Provider internal credentials/details")));
        }
        if ("tool-request".equals(query)) {
            var request = ToolExecutionRequest.builder().name("getLowStockProducts")
                    .arguments("{\"internal\":\"not for the client\"}").build();
            return Multi.createFrom().items(
                    new ChatEvent.PartialThinkingEvent("Internal thinking must not be exposed"),
                    new ChatEvent.IntermediateResponseEvent(ChatResponse.builder().aiMessage(AiMessage.from(request)).build()),
                    new ChatEvent.BeforeToolExecutionEvent(request),
                    new ChatEvent.ToolExecutedEvent(ToolExecution.builder().request(request)
                            .invocationContext(InvocationContext.builder().chatMemoryId(conversationId).build())
                            .result("Internal tool result must not be exposed").build()),
                    new ChatEvent.PartialResponseEvent("Three products are low stock."),
                    new ChatEvent.ChatCompletedEvent(ChatResponse.builder()
                            .aiMessage(AiMessage.from("Three products are low stock.")).build()));
        }
        return Multi.createFrom().items(new ChatEvent.PartialResponseEvent("I can help "),
                new ChatEvent.PartialResponseEvent("with your business questions."),
                new ChatEvent.ChatCompletedEvent(ChatResponse.builder()
                        .aiMessage(AiMessage.from(ChatResourceTest.AI_RESPONSE)).build()));
    }

    @Override
    public BusinessAnalysisDTO chat(UUID conversationId, String query) {
        lastConversationId = conversationId;
        lastQuery = query;
        if ("provider-failure".equals(query)) {
            throw new IllegalStateException("Provider internal credentials/details");
        }
        return new BusinessAnalysisDTO(
                ChatResourceTest.AI_RESPONSE,
                ChatResourceTest.INSIGHTS,
                ChatResourceTest.RECOMMENDATIONS
        );
    }
}
