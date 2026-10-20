package com.aibusinessassistant.chat;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.aibusinessassistant.chat.ai.BusinessAnalysisAiAnswer;
import com.aibusinessassistant.chat.ai.BusinessAnalysisService;
import com.aibusinessassistant.chat.ai.AiProvider;
import com.aibusinessassistant.chat.ai.GeminiAiService;
import com.aibusinessassistant.chat.ai.OllamaAiService;
import com.aibusinessassistant.chat.dto.BusinessAnalysisDTO;
import com.aibusinessassistant.chat.dto.ChatRequestDTO;
import com.aibusinessassistant.chat.dto.ChatStreamEventDTO;
import com.aibusinessassistant.chat.dto.StreamEventType;
import com.aibusinessassistant.chat.history.ConversationMessageRepository;
import com.aibusinessassistant.chat.history.ConversationRepository;
import com.aibusinessassistant.knowledge.KnowledgeEvidence;
import com.aibusinessassistant.knowledge.RetrievedChunk;
import com.aibusinessassistant.knowledge.dto.KnowledgeSourceDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import io.smallrye.mutiny.operators.multi.processors.UnicastProcessor;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
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
    // Answered by the fakes as a policy question: Gemini searches the knowledge base and cites INV-8 plus an
    // invented id; Ollama's model fills sources in itself.
    static final String POLICY_QUERY = "policy-request";
    static final RetrievedChunk INV_8 = new RetrievedChunk("inventory-policy.md",
            "Inventory policy violations and the low-stock list", "INV-8",
            "Inventory Policy > Inventory policy violations and the low-stock list\n**INV-8** Only HEALTHY complies.",
            0.74);
    static final KnowledgeSourceDTO INV_8_SOURCE = new KnowledgeSourceDTO(INV_8.source(), INV_8.section(),
            INV_8.ruleId());

    @Inject
    ConversationMessageRepository messages;

    @Inject
    ConversationRepository conversations;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ChatService chatService;

    @Inject
    Vertx vertx;

    @Test
    void omittedProviderUsesTheConfiguredDefault() {
        UUID id = UUID.randomUUID();
        given().contentType(ContentType.JSON)
                .body("{\"conversationId\":\"" + id + "\",\"query\":\"test\"}")
                .when().post("/api/chat").then().statusCode(200);

        assertEquals(AiProvider.GEMINI, TestBusinessAnalysisService.lastProvider);
        assertEquals(AiProvider.GEMINI, conversations.findById(id).getProvider());
    }

    @Test
    void bothProvidersWorkAcrossStructuredAndStreamingFollowUps() throws JsonProcessingException {
        for (AiProvider provider : AiProvider.values()) {
            UUID id = UUID.randomUUID();
            given().contentType(ContentType.JSON)
                    .body(new ChatRequestDTO(id.toString(), QUERY, provider))
                    .when().post("/api/chat").then().statusCode(200);
            assertEquals(provider, TestBusinessAnalysisService.lastProvider);
            assertEquals(provider, conversations.findById(id).getProvider());

            // An omitted provider uses the stored selection, even when it differs from the default.
            streamEvents(id, "follow-up");
            assertEquals(provider, TestBusinessAnalysisService.lastProvider);
            assertEquals(provider, conversations.findById(id).getProvider());

            given().contentType(ContentType.JSON).accept("text/event-stream")
                    .body(new ChatRequestDTO(id.toString(), QUERY, provider))
                    .when().post("/api/chat/stream").then().statusCode(200)
                    .contentType("text/event-stream");
            assertEquals(provider, TestBusinessAnalysisService.lastProvider);
            assertEquals(6, messages.listByConversationId(id).size());
        }
    }

    @Test
    void streamingFirstRequestPinsTheProviderForStructuredFollowUps() {
        for (AiProvider provider : AiProvider.values()) {
            UUID id = UUID.randomUUID();
            given().contentType(ContentType.JSON).accept("text/event-stream")
                    .body(new ChatRequestDTO(id.toString(), QUERY, provider))
                    .when().post("/api/chat/stream").then().statusCode(200);
            assertEquals(provider, TestBusinessAnalysisService.lastProvider);

            given().contentType(ContentType.JSON)
                    .body(new ChatRequestDTO(id.toString(), "follow-up"))
                    .when().post("/api/chat").then().statusCode(200);
            assertEquals(provider, TestBusinessAnalysisService.lastProvider);
            assertEquals(provider, conversations.findById(id).getProvider());
        }
    }

    @Test
    void changingProviderIsRejectedBeforeEitherModelOrSseIsInvoked() {
        for (AiProvider provider : AiProvider.values()) {
            UUID id = UUID.randomUUID();
            given().contentType(ContentType.JSON)
                    .body(new ChatRequestDTO(id.toString(), QUERY, provider))
                    .when().post("/api/chat").then().statusCode(200);
            AiProvider other = provider == AiProvider.GEMINI ? AiProvider.OLLAMA : AiProvider.GEMINI;
            int callsBefore = TestBusinessAnalysisService.invocations;

            for (String endpoint : List.of("/api/chat", "/api/chat/stream")) {
                given().contentType(ContentType.JSON)
                        .accept(endpoint.endsWith("/stream") ? "text/event-stream" : "application/json")
                        .body(new ChatRequestDTO(id.toString(), "switch", other))
                        .when().post(endpoint).then().statusCode(409);
            }

            assertEquals(callsBefore, TestBusinessAnalysisService.invocations);
            assertEquals(provider, conversations.findById(id).getProvider());
            assertEquals(2, messages.listByConversationId(id).size());
        }
    }

    @Test
    void invalidProvidersAreRejectedBeforeCreatingConversationOrCallingModel() {
        for (String endpoint : List.of("/api/chat", "/api/chat/stream")) {
            for (String value : List.of("\"unknown\"", "\"\"", "42")) {
                UUID id = UUID.randomUUID();
                int callsBefore = TestBusinessAnalysisService.invocations;
                given().contentType(ContentType.JSON)
                        .body("{\"conversationId\":\"" + id + "\",\"query\":\"test\",\"provider\":" + value + "}")
                        .when().post(endpoint).then().statusCode(400);
                assertEquals(callsBefore, TestBusinessAnalysisService.invocations);
                assertNull(conversations.findById(id));
            }
        }
    }

    @Test
    void providerNamesAcceptCaseAndSurroundingWhitespace() {
        UUID id = UUID.randomUUID();
        given().contentType(ContentType.JSON)
                .body("{\"conversationId\":\"" + id + "\",\"query\":\"test\",\"provider\":\" GeMiNi \"}")
                .when().post("/api/chat").then().statusCode(200);
        assertEquals(AiProvider.GEMINI, TestBusinessAnalysisService.lastProvider);
        assertEquals(AiProvider.GEMINI, conversations.findById(id).getProvider());
    }

    @Test
    void normalChatServiceRethrowsAiFailureWithoutRecordingSuccessfulHistory() {
        UUID conversationId = UUID.randomUUID();
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> chatService.chat(conversationId, "provider-failure"));

        assertEquals("Provider internal credentials/details", failure.getMessage());
        assertEquals(conversationId, TestBusinessAnalysisService.lastConversationId);
        assertEquals("provider-failure", TestBusinessAnalysisService.lastQuery);
        assertEquals(0, messages.listByConversationId(conversationId).size());
        assertEquals(AiProvider.GEMINI, conversations.findById(conversationId).getProvider());
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
    void chunkFollowedImmediatelyByFailureIsDeliveredBeforeTheErrorWithoutDone() {
        UUID conversationId = UUID.randomUUID();
        UnicastProcessor<ChatEvent> upstream = UnicastProcessor.create();
        TestBusinessAnalysisService.scriptedStream = upstream;
        AssertSubscriber<ChatStreamEventDTO> subscriber = chatService.chatStream(conversationId, "scripted")
                .subscribe().withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));

        // The test thread emits both signals back to back; no scheduler decides their order.
        upstream.onNext(new ChatEvent.PartialResponseEvent("Partial answer"));
        upstream.onError(new IllegalStateException("Provider internal credentials/details"));

        // Asserted without waiting: items and failures must not be buffered behind another thread.
        subscriber.assertCompleted()
                .assertItems(new ChatStreamEventDTO(StreamEventType.CHUNK, "Partial answer"), safeError());
        assertEquals(0, messages.listByConversationId(conversationId).size());
    }

    @Test
    void completionOnTheEventLoopPersistsHistoryOnAWorkerBeforeDone() throws JsonProcessingException {
        UUID conversationId = UUID.randomUUID();
        // Created from the test thread, so this is an event-loop context.
        Context eventLoop = vertx.getOrCreateContext();
        AtomicBoolean emittedOnEventLoop = new AtomicBoolean();
        TestBusinessAnalysisService.scriptedStream = Multi.createFrom().emitter(emitter ->
                eventLoop.runOnContext(ignored -> {
                    emittedOnEventLoop.set(Context.isOnEventLoopThread());
                    emitter.emit(new ChatEvent.PartialResponseEvent("I can help "));
                    emitter.emit(new ChatEvent.PartialResponseEvent("with your business questions."));
                    emitter.complete();
                }));

        // Blocking JDBC on the event loop would fail, turning DONE into ERROR and rolling back history.
        assertEquals(List.of(new ChatStreamEventDTO(StreamEventType.CHUNK, "I can help "),
                new ChatStreamEventDTO(StreamEventType.CHUNK, "with your business questions."),
                new ChatStreamEventDTO(StreamEventType.DONE, null)), streamEvents(conversationId, "scripted"));
        assertTrue(emittedOnEventLoop.get());
        var history = messages.listByConversationId(conversationId);
        assertEquals(2, history.size());
        assertEquals("scripted", history.getFirst().getContent());
        assertEquals(AI_RESPONSE, history.getLast().getContent());
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
    void geminiPolicyAnswerReturnsOnlyRetrievedCitedSourcesAndStoresThem() throws JsonProcessingException {
        UUID conversationId = UUID.randomUUID();
        BusinessAnalysisDTO response = given()
                .contentType(ContentType.JSON)
                .body(new ChatRequestDTO(conversationId.toString(), POLICY_QUERY, AiProvider.GEMINI))
                .when().post("/api/chat")
                .then().statusCode(200).contentType(ContentType.JSON)
                .extract().as(BusinessAnalysisDTO.class);

        // The invented INV-99 is dropped.
        BusinessAnalysisDTO expected = new BusinessAnalysisDTO(AI_RESPONSE, INSIGHTS, RECOMMENDATIONS,
                List.of(INV_8_SOURCE));
        assertEquals(expected, response);
        assertEquals(expected, objectMapper.readValue(
                messages.listByConversationId(conversationId).getLast().getContent(), BusinessAnalysisDTO.class));
    }

    @Test
    void dataAnswersAndOllamaAnswersHaveEmptySources() {
        given().contentType(ContentType.JSON)
                .body(new ChatRequestDTO(UUID.randomUUID().toString(), QUERY, AiProvider.GEMINI))
                .when().post("/api/chat")
                .then().statusCode(200).body("sources", is(List.of()));

        // Ollama's fake model fills sources in itself; the adapter discards them.
        given().contentType(ContentType.JSON)
                .body(new ChatRequestDTO(UUID.randomUUID().toString(), POLICY_QUERY, AiProvider.OLLAMA))
                .when().post("/api/chat")
                .then().statusCode(200)
                .body("summary", is(AI_RESPONSE))
                .body("sources", is(List.of()));
    }

    @Test
    void historyStoredWithoutSourcesStillReads() throws JsonProcessingException {
        String storedBeforeSources = objectMapper.writeValueAsString(
                Map.of("summary", AI_RESPONSE, "insights", INSIGHTS, "recommendations", RECOMMENDATIONS));

        assertEquals(new BusinessAnalysisDTO(AI_RESPONSE, INSIGHTS, RECOMMENDATIONS, List.of()),
                objectMapper.readValue(storedBeforeSources, BusinessAnalysisDTO.class));
    }

    @Test
    void knowledgeSearchStreamEventsContainOnlyTheToolName() throws JsonProcessingException {
        UUID conversationId = UUID.randomUUID();
        assertEquals(List.of(new ChatStreamEventDTO(StreamEventType.TOOL_STARTED, "searchKnowledgeBase"),
                new ChatStreamEventDTO(StreamEventType.TOOL_COMPLETED, "searchKnowledgeBase"),
                new ChatStreamEventDTO(StreamEventType.CHUNK, "USB-C Cable is CRITICAL under INV-3."),
                new ChatStreamEventDTO(StreamEventType.DONE, null)),
                streamEvents(conversationId, "knowledge-tool-request"));
        assertEquals("USB-C Cable is CRITICAL under INV-3.",
                messages.listByConversationId(conversationId).getLast().getContent());
    }

    static ToolExecution policySearch(UUID conversationId) {
        KnowledgeEvidence evidence = new KnowledgeEvidence(List.of(INV_8));
        return ToolExecution.builder()
                .request(ToolExecutionRequest.builder().name("searchKnowledgeBase")
                        .arguments("{\"query\":\"inventory policy violations\"}").build())
                .result(ToolExecutionResult.builder().result(evidence).resultText(evidence.format()).build())
                .invocationContext(InvocationContext.builder().chatMemoryId(conversationId).build())
                .build();
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

class TestBusinessAnalysisService implements BusinessAnalysisService {

    static volatile String lastQuery;
    static volatile UUID lastConversationId;
    static volatile AiProvider lastProvider;
    static volatile int invocations;
    // Returned for the "scripted" query so a test controls exactly when and where events are emitted.
    static volatile Multi<ChatEvent> scriptedStream;

    private final AiProvider provider;

    TestBusinessAnalysisService(AiProvider provider) {
        this.provider = provider;
    }

    @Override
    public Multi<ChatEvent> chatStream(UUID conversationId, String query) {
        lastProvider = provider;
        invocations++;
        lastConversationId = conversationId;
        lastQuery = query;
        if ("provider-failure".equals(query)) {
            return Multi.createFrom().failure(new IllegalStateException("Provider internal credentials/details"));
        }
        if ("synchronous-failure".equals(query)) {
            throw new IllegalStateException("Provider internal credentials/details");
        }
        if ("scripted".equals(query)) {
            return scriptedStream;
        }
        if ("partial-failure".equals(query)) {
            return Multi.createBy().concatenating().streams(
                    Multi.createFrom().item(new ChatEvent.PartialResponseEvent("Partial answer")),
                    Multi.createFrom().failure(new IllegalStateException("Provider internal credentials/details")));
        }
        if ("knowledge-tool-request".equals(query)) {
            ToolExecution search = ChatResourceTest.policySearch(conversationId);
            return Multi.createFrom().items(
                    new ChatEvent.BeforeToolExecutionEvent(search.request()),
                    new ChatEvent.ToolExecutedEvent(search),
                    new ChatEvent.PartialResponseEvent("USB-C Cable is CRITICAL under INV-3."),
                    new ChatEvent.ChatCompletedEvent(ChatResponse.builder()
                            .aiMessage(AiMessage.from("USB-C Cable is CRITICAL under INV-3.")).build()));
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
        lastProvider = provider;
        invocations++;
        lastConversationId = conversationId;
        lastQuery = query;
        if ("provider-failure".equals(query)) {
            throw new IllegalStateException("Provider internal credentials/details");
        }
        if (ChatResourceTest.POLICY_QUERY.equals(query)) {
            return new BusinessAnalysisDTO(ChatResourceTest.AI_RESPONSE, ChatResourceTest.INSIGHTS,
                    ChatResourceTest.RECOMMENDATIONS, List.of(ChatResourceTest.INV_8_SOURCE));
        }
        return new BusinessAnalysisDTO(
                ChatResourceTest.AI_RESPONSE,
                ChatResourceTest.INSIGHTS,
                ChatResourceTest.RECOMMENDATIONS
        );
    }
}

@Alternative
@Priority(1)
@ApplicationScoped
class TestGeminiAiService implements GeminiAiService {
    private final TestBusinessAnalysisService delegate = new TestBusinessAnalysisService(AiProvider.GEMINI);

    @Override
    public Result<BusinessAnalysisAiAnswer> chat(UUID conversationId, String query) {
        BusinessAnalysisDTO answer = delegate.chat(conversationId, query);
        boolean policyQuery = ChatResourceTest.POLICY_QUERY.equals(query);
        return Result.<BusinessAnalysisAiAnswer>builder()
                .content(new BusinessAnalysisAiAnswer(answer.summary(), answer.insights(), answer.recommendations(),
                        policyQuery ? List.of("INV-8", "INV-99") : List.of()))
                .toolExecutions(policyQuery ? List.of(ChatResourceTest.policySearch(conversationId)) : List.of())
                .build();
    }

    @Override
    public Multi<ChatEvent> chatStream(UUID conversationId, String query) {
        return delegate.chatStream(conversationId, query);
    }
}

@Alternative
@Priority(1)
@ApplicationScoped
class TestOllamaAiService implements OllamaAiService {
    private final TestBusinessAnalysisService delegate = new TestBusinessAnalysisService(AiProvider.OLLAMA);

    @Override
    public BusinessAnalysisDTO chat(UUID conversationId, String query) {
        return delegate.chat(conversationId, query);
    }

    @Override
    public Multi<ChatEvent> chatStream(UUID conversationId, String query) {
        return delegate.chatStream(conversationId, query);
    }
}
