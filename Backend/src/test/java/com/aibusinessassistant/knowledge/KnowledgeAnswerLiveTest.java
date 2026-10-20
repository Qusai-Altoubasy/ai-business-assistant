package com.aibusinessassistant.knowledge;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.aibusinessassistant.knowledge.dto.KnowledgeAnswerDTO;
import com.aibusinessassistant.knowledge.dto.KnowledgeSourceDTO;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

// Calls the real Gemini embedding and chat models, so it runs only on request:
// ./mvnw test -Dtest=KnowledgeAnswerLiveTest -Dgemini.live=true (with GEMINI_API_KEY and GEMINI_MODEL set).
// It indexes the real knowledge base and checks the sources and the INSUFFICIENT answer, never the model's wording.
@QuarkusTest
@TestProfile(KnowledgeAnswerLiveTest.RealGeminiChat.class)
@EnabledIfSystemProperty(named = "gemini.live", matches = "true")
class KnowledgeAnswerLiveTest {

    private static final String RETURN_POLICY = "What is our return policy?";
    private static final String SHIPPING_POLICY = "What is our shipping policy?";
    private static final String CRITICAL_PRODUCT = "When is a product considered critical?";

    @Inject
    KnowledgeBaseIndexer indexer;

    @Inject
    KnowledgeAnswerService answerService;

    @Inject
    KnowledgeAiService aiService;

    @Inject
    KnowledgeRetriever retriever;

    @Inject
    KnowledgeChunkRepository repository;

    @Inject
    EmbeddingService embeddingService;

    @Inject
    UserMessageRecorder userMessages;

    @Inject
    @ConfigProperty(name = "app.knowledge-base.retrieval.top-k")
    int topK;

    @Inject
    @ConfigProperty(name = "app.knowledge-base.retrieval.min-similarity")
    double minSimilarity;

    @Test
    @TestTransaction
    void answersAreBasedOnlyOnRetrievedPolicies() {
        indexer.indexAll();

        KnowledgeAnswerDTO returnPolicy = answer(RETURN_POLICY);
        KnowledgeAnswerDTO shippingPolicy = answer(SHIPPING_POLICY);

        // The model itself, given real evidence that does not answer the question.
        printClosestChunks(CRITICAL_PRODUCT);
        KnowledgeEvidence inventory = new KnowledgeEvidence(retriever.retrieve(CRITICAL_PRODUCT));
        userMessages.clear();
        KnowledgeAiAnswer unrelated = aiService.answer(inventory.format(), SHIPPING_POLICY);
        List<KnowledgeSourceDTO> unrelatedSources = inventory.sourcesFor(unrelated.citations());
        System.out.println("    " + SHIPPING_POLICY + " with the evidence above");
        System.out.println("    citations: " + unrelated.citations() + ", kept: " + unrelatedSources);
        System.out.println("    answer: " + unrelated.answer());

        assertAll(
                () -> assertNotEquals(KnowledgeAnswerService.INSUFFICIENT, returnPolicy.answer(), RETURN_POLICY),
                () -> assertTrue(returnPolicy.sources().stream()
                        .anyMatch(source -> source.ruleId() != null && source.ruleId().startsWith("REF-")),
                        RETURN_POLICY + " sources " + returnPolicy.sources()),
                () -> assertEquals(new KnowledgeAnswerDTO(KnowledgeAnswerService.INSUFFICIENT, List.of()),
                        shippingPolicy, SHIPPING_POLICY),
                () -> assertFalse(inventory.isEmpty(), CRITICAL_PRODUCT + " found no evidence"),
                () -> assertEquals(List.of(), unrelatedSources, "citations " + unrelated.citations()),
                // The <chunk> tags reach the model as written, not escaped by the prompt template.
                () -> assertTrue(userMessages.last().contains("<chunk id=\"INV-"), userMessages.last()),
                () -> assertFalse(userMessages.last().contains("&lt;"), userMessages.last()));
    }

    private KnowledgeAnswerDTO answer(String question) {
        printClosestChunks(question);
        KnowledgeAnswerDTO answer = answerService.answer(question);
        System.out.println("    sources: " + answer.sources());
        System.out.println("    answer: " + answer.answer());
        return answer;
    }

    // The top-k chunks by similarity without the threshold, each marked kept or dropped by it.
    private void printClosestChunks(String question) {
        System.out.println("QUESTION " + question);
        for (RetrievedChunk chunk : repository.findClosest(embeddingService.embedQuery(question),
                embeddingService.modelName(), topK)) {
            System.out.printf("    %-28s %.4f %s%n", KnowledgeEvidence.chunkId(chunk), chunk.similarity(),
                    chunk.similarity() >= minSimilarity ? "kept" : "dropped");
        }
    }

    // Replaces the %test placeholder key and chat model id with the real ones from the environment.
    public static class RealGeminiChat implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.langchain4j.ai.gemini.business-gemini.api-key", "${GEMINI_API_KEY}",
                    "quarkus.langchain4j.ai.gemini.business-gemini.chat-model.model-id", "${GEMINI_MODEL}");
        }

        @Override
        public Set<Class<?>> getEnabledAlternatives() {
            return Set.of(UserMessageRecorder.class);
        }
    }

    // Keeps the text of the user messages sent to the chat model; enabled only by RealGeminiChat.
    @Alternative
    @ApplicationScoped
    public static class UserMessageRecorder implements ChatModelListener {

        private final List<String> texts = new CopyOnWriteArrayList<>();

        @Override
        public void onRequest(ChatModelRequestContext requestContext) {
            for (ChatMessage message : requestContext.chatRequest().messages()) {
                if (message instanceof UserMessage user && user.hasSingleText()) {
                    texts.add(user.singleText());
                }
            }
        }

        public void clear() {
            texts.clear();
        }

        public String last() {
            return texts.isEmpty() ? "" : texts.get(texts.size() - 1);
        }
    }
}
