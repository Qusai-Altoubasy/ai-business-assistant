package com.aibusinessassistant.knowledge;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.function.Executable;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

// Calls the real Gemini API, so it runs only on request:
// ./mvnw test -Dtest=KnowledgeRetrieverLiveTest -Dgemini.live=true (with GEMINI_API_KEY set).
// It indexes the real knowledge base and checks that questions find the right rule and that
// questions the knowledge base does not answer find no evidence with the configured threshold.
@QuarkusTest
@TestProfile(GeminiEmbeddingLiveTest.RealGeminiKey.class)
@EnabledIfSystemProperty(named = "gemini.live", matches = "true")
class KnowledgeRetrieverLiveTest {

    // Each question with the rule its answer comes from.
    private static final Map<String, String> IN_SCOPE = Map.of(
            "When is a product considered critical?", "INV-3",
            "How much stock should we reorder for a low-stock product?", "INV-11",
            "Can a customer order more units than we have in stock?", "INV-13",
            "How many days does a customer have to return an item?", "REF-1",
            "Is there a restocking fee when a monitor is returned?", "REF-9",
            "How long does it take to receive a refund?", "REF-10",
            "When is a customer a key account?", "CUS-2",
            "Can reports show a customer's email address?", "CUS-10");

    // Questions the knowledge base does not answer; shipping was removed from it on purpose.
    private static final List<String> OUT_OF_SCOPE = List.of(
            "What is our shipping policy?",
            "Which payment methods do we accept?",
            "How is sales tax calculated on an order?",
            "How many vacation days do employees get?",
            "What is the capital of France?");

    @Inject
    KnowledgeBaseIndexer indexer;

    @Inject
    KnowledgeRetriever retriever;

    @Inject
    KnowledgeChunkRepository repository;

    @Inject
    EmbeddingService embeddingService;

    @Inject
    @ConfigProperty(name = "app.knowledge-base.retrieval.top-k")
    int topK;

    @Inject
    @ConfigProperty(name = "app.knowledge-base.retrieval.min-similarity")
    double minSimilarity;

    @Test
    @TestTransaction
    void questionsFindTheirRuleAndUnansweredQuestionsFindNoEvidence() {
        indexer.indexAll();
        printClosestChunks();

        List<Executable> checks = new ArrayList<>();
        IN_SCOPE.forEach((question, ruleId) -> checks.add(() -> {
            List<String> ruleIds = retriever.retrieve(question).stream().map(RetrievedChunk::ruleId).toList();
            assertTrue(ruleIds.contains(ruleId), question + " expected " + ruleId + " but got " + ruleIds);
        }));
        OUT_OF_SCOPE.forEach(question -> checks.add(() ->
                assertEquals(List.of(), retriever.retrieve(question), question)));
        assertAll(checks);
    }

    // Shows the top-k chunks the retriever ranks for every question, each marked kept or dropped by the
    // threshold, so the printed table matches what retrieve() returns and the threshold can be calibrated.
    private void printClosestChunks() {
        List<String> questions = new ArrayList<>(IN_SCOPE.keySet());
        questions.addAll(OUT_OF_SCOPE);
        for (String question : questions) {
            System.out.println((IN_SCOPE.containsKey(question) ? "IN  " : "OUT ") + question);
            List<RetrievedChunk> closest = repository.findClosest(embeddingService.embedQuery(question),
                    embeddingService.modelName(), topK);
            for (RetrievedChunk chunk : closest) {
                System.out.printf("    %-8s %-20s %.4f %s%n", chunk.ruleId() == null ? "preamble" : chunk.ruleId(),
                        chunk.source(), chunk.similarity(), chunk.similarity() >= minSimilarity ? "kept" : "dropped");
            }
        }
    }
}
