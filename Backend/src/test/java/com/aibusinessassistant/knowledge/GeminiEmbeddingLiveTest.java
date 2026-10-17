package com.aibusinessassistant.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

// Calls the real Gemini API, so it runs only on request:
// ./mvnw test -Dtest=GeminiEmbeddingLiveTest -Dgemini.live=true (with GEMINI_API_KEY set).
@QuarkusTest
@TestProfile(GeminiEmbeddingLiveTest.RealGeminiKey.class)
@EnabledIfSystemProperty(named = "gemini.live", matches = "true")
class GeminiEmbeddingLiveTest {

    @Inject
    EmbeddingService service;

    @Test
    void geminiReturns768DimensionsForDocumentsAndQuestions() {
        float[] document = service.embedDocument("A product is OUT_OF_STOCK when stock_quantity = 0.");
        float[] question = service.embedQuery("Which products are out of stock?");

        assertEquals(EmbeddingService.DIMENSION, document.length);
        assertEquals(EmbeddingService.DIMENSION, question.length);
    }

    // Replaces the %test placeholder key with the real one from the environment.
    public static class RealGeminiKey implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.langchain4j.ai.gemini.business-gemini.api-key", "${GEMINI_API_KEY}");
        }
    }
}
