package com.aibusinessassistant.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

// Checks the configured Gemini embedding model without calling it: the model reports its configured
// output dimension, so no request reaches Gemini and the %test API key is enough.
@QuarkusTest
class EmbeddingModelConfigTest {

    @Inject
    EmbeddingService service;

    @Inject
    @ModelName("business-gemini")
    EmbeddingModel model;

    @Test
    void serviceUsesTheConfiguredGeminiEmbeddingModel() {
        assertEquals("gemini-embedding-001", service.modelName());
    }

    @Test
    void configuredDimensionMatchesTheKnowledgeChunksColumn() {
        assertEquals(EmbeddingService.DIMENSION, model.dimension());
    }
}
