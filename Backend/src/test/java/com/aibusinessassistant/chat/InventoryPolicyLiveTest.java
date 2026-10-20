package com.aibusinessassistant.chat;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.aibusinessassistant.chat.ai.GeminiBusinessAnalysisService;
import com.aibusinessassistant.chat.dto.BusinessAnalysisDTO;
import com.aibusinessassistant.knowledge.EmbeddingService;
import com.aibusinessassistant.knowledge.KnowledgeBaseIndexer;
import com.aibusinessassistant.knowledge.KnowledgeChunkRepository;
import com.aibusinessassistant.knowledge.KnowledgeEvidence;
import com.aibusinessassistant.knowledge.KnowledgeRetriever;
import com.aibusinessassistant.knowledge.RetrievedChunk;
import com.aibusinessassistant.order.OrderItemRepository;
import com.aibusinessassistant.product.ProductRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

// Calls the real Gemini embedding and chat models, so it runs only on request:
// ./mvnw test -Dtest=InventoryPolicyLiveTest -Dgemini.live=true (with GEMINI_API_KEY and GEMINI_MODEL set).
// It checks tool names, rule IDs and product names, never the model's wording.
@QuarkusTest
@TestProfile(InventoryPolicyLiveTest.RealGeminiChat.class)
@EnabledIfSystemProperty(named = "gemini.live", matches = "true")
class InventoryPolicyLiveTest {

    private static final String QUESTION = "Which products currently violate our inventory policy?";
    private static final List<String> VIOLATING = List.of("Laptop Stand", "USB-C Cable", "Keyboard", "USB-C Hub");
    private static final List<String> HEALTHY = List.of("Wireless Mouse", "27-inch Monitor", "Webcam", "Headset",
            "Desk Lamp", "Notebook");

    @Inject
    KnowledgeBaseIndexer indexer;

    @Inject
    GeminiBusinessAnalysisService gemini;

    @Inject
    KnowledgeRetriever retriever;

    @Inject
    KnowledgeChunkRepository repository;

    @Inject
    EmbeddingService embeddingService;

    @Inject
    OrderItemRepository orderItems;

    @Inject
    ProductRepository products;

    @Inject
    ToolCallRecorder toolCalls;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    @ConfigProperty(name = "app.knowledge-base.retrieval.top-k")
    int topK;

    @Inject
    @ConfigProperty(name = "app.knowledge-base.retrieval.min-similarity")
    double minSimilarity;

    @Test
    @TestTransaction
    void violationsCombineInventoryDataAndThePolicy() throws JsonProcessingException {
        indexer.indexAll();

        BusinessAnalysisDTO response = ask("7.7 violations");
        List<String> findings = Stream.concat(response.insights().stream(), response.recommendations().stream())
                .toList();

        assertAll(
                () -> assertTrue(toolCalls.names().containsAll(List.of("getInventorySnapshot", "searchKnowledgeBase")),
                        "tools called " + toolCalls.names()),
                () -> assertTrue(response.sources().stream().anyMatch(source -> "INV-8".equals(source.ruleId())),
                        "sources " + response.sources()),
                () -> assertTrue(VIOLATING.stream().allMatch(name -> findings.stream().anyMatch(line -> line.contains(name))),
                        "violating products missing from " + findings),
                () -> assertTrue(HEALTHY.stream().noneMatch(name -> response.recommendations().stream()
                        .anyMatch(line -> line.contains(name))), "recommendations " + response.recommendations()),
                () -> assertTrue(text(response).stream().noneMatch(line -> line.contains("Notebook")
                        && line.contains("OVERSTOCK")), "Notebook described as OVERSTOCK in " + text(response)));
    }

    @Test
    @TestTransaction
    void missingPolicyGivesNoDefinitiveAnalysis() throws JsonProcessingException {
        // The test profile does not index at startup, so no chunk of the real embedding model exists.
        assertEquals(List.of(), retriever.retrieve(QUESTION));

        BusinessAnalysisDTO response = ask("7.10 missing policy");

        assertAll(
                () -> assertEquals(List.of(), response.sources()),
                () -> assertTrue(text(response).stream().noneMatch(line -> line.contains("INV-")),
                        "rule IDs in " + text(response)));
    }

    @Test
    @TestTransaction
    void missingInventoryGivesNoProductConclusion() throws JsonProcessingException {
        indexer.indexAll();
        // Order items reference products, so they are removed first; the transaction rolls both back.
        orderItems.deleteAll();
        products.deleteAll();

        BusinessAnalysisDTO response = ask("7.10 missing inventory");

        List<String> seeded = Stream.concat(VIOLATING.stream(), HEALTHY.stream()).toList();
        assertTrue(seeded.stream().noneMatch(name -> text(response).stream().anyMatch(line -> line.contains(name))),
                "product names in " + text(response));
    }

    private BusinessAnalysisDTO ask(String label) throws JsonProcessingException {
        toolCalls.clear();
        BusinessAnalysisDTO response = gemini.chat(UUID.randomUUID(), QUESTION);

        System.out.println("CASE " + label + ": " + QUESTION);
        for (ToolExecutionRequest call : toolCalls.calls()) {
            System.out.println("    TOOL " + call.name() + " " + call.arguments());
            if ("searchKnowledgeBase".equals(call.name())) {
                printClosestChunks(objectMapper.readTree(call.arguments()).path("query").asText());
            }
        }
        System.out.println("    sources: " + response.sources());
        System.out.println("    summary: " + response.summary());
        response.insights().forEach(line -> System.out.println("    insight: " + line));
        response.recommendations().forEach(line -> System.out.println("    recommendation: " + line));
        return response;
    }

    // The top-k chunks by similarity without the threshold, each marked kept or dropped by it.
    private void printClosestChunks(String query) {
        System.out.println("        QUERY " + query);
        for (RetrievedChunk chunk : repository.findClosest(embeddingService.embedQuery(query),
                embeddingService.modelName(), topK)) {
            System.out.printf("        %-28s %.4f %s%n", KnowledgeEvidence.chunkId(chunk), chunk.similarity(),
                    chunk.similarity() >= minSimilarity ? "kept" : "dropped");
        }
    }

    private static List<String> text(BusinessAnalysisDTO response) {
        List<String> lines = new ArrayList<>();
        lines.add(response.summary());
        lines.addAll(response.insights());
        lines.addAll(response.recommendations());
        return lines;
    }

    // Replaces the %test placeholder key and chat model id with the real ones from the environment, and removes the
    // ChatResourceTest replacement of GeminiAiService so the real Gemini service runs.
    public static class RealGeminiChat implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.langchain4j.ai.gemini.business-gemini.api-key", "${GEMINI_API_KEY}",
                    "quarkus.langchain4j.ai.gemini.business-gemini.chat-model.model-id", "${GEMINI_MODEL}",
                    "quarkus.arc.exclude-types", TestGeminiAiService.class.getName());
        }

        @Override
        public Set<Class<?>> getEnabledAlternatives() {
            return Set.of(ToolCallRecorder.class);
        }
    }

    // Keeps the tool calls the chat model requests; enabled only by RealGeminiChat.
    @Alternative
    @ApplicationScoped
    public static class ToolCallRecorder implements ChatModelListener {

        private final List<ToolExecutionRequest> calls = new CopyOnWriteArrayList<>();

        @Override
        public void onResponse(ChatModelResponseContext responseContext) {
            AiMessage message = responseContext.chatResponse().aiMessage();
            if (message != null && message.hasToolExecutionRequests()) {
                calls.addAll(message.toolExecutionRequests());
            }
        }

        public void clear() {
            calls.clear();
        }

        public List<ToolExecutionRequest> calls() {
            return List.copyOf(calls);
        }

        public List<String> names() {
            return calls.stream().map(ToolExecutionRequest::name).toList();
        }
    }
}
