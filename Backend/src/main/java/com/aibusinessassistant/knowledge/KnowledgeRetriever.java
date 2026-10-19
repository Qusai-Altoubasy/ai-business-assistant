package com.aibusinessassistant.knowledge;

import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Finds the knowledge-base chunks closest to a question. Only the top-k closest chunks whose similarity
 * reaches min-similarity count as evidence; an empty list means the knowledge base has no valid evidence.
 */
@ApplicationScoped
public class KnowledgeRetriever {

    private final EmbeddingService embeddingService;
    private final KnowledgeChunkRepository repository;
    private final int topK;
    private final double minSimilarity;

    public KnowledgeRetriever(EmbeddingService embeddingService, KnowledgeChunkRepository repository,
                              @ConfigProperty(name = "app.knowledge-base.retrieval.top-k") int topK,
                              @ConfigProperty(name = "app.knowledge-base.retrieval.min-similarity") double minSimilarity) {
        this.embeddingService = embeddingService;
        this.repository = repository;
        this.topK = topK;
        this.minSimilarity = minSimilarity;
    }

    // A blank question has nothing to search for, so the embedding model is not called.
    public List<RetrievedChunk> retrieve(String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        float[] vector = embeddingService.embedQuery(question);
        return repository.findClosest(vector, embeddingService.modelName(), topK).stream()
                .filter(chunk -> chunk.similarity() >= minSimilarity)
                .toList();
    }
}
