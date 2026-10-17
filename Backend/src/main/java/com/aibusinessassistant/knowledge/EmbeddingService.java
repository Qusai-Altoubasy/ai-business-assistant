package com.aibusinessassistant.knowledge;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.request.EmbeddingInputType;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import io.quarkiverse.langchain4j.ModelName;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EmbeddingService {

    // Mirrors knowledge_chunks.embedding vector(768) from migration V5.
    public static final int DIMENSION = 768;

    private final EmbeddingModel model;

    // Written by hand because Lombok does not copy the @ModelName qualifier to the constructor.
    public EmbeddingService(@ModelName("business-gemini") EmbeddingModel model) {
        this.model = model;
    }

    public float[] embedDocument(String text) {
        return embed(text, EmbeddingInputType.DOCUMENT);
    }

    public float[] embedQuery(String text) {
        return embed(text, EmbeddingInputType.QUERY);
    }

    public String modelName() {
        return model.modelName();
    }

    private float[] embed(String text, EmbeddingInputType type) {
        float[] vector = model.embed(EmbeddingRequest.builder().input(text).inputType(type).build())
                .embeddings().getFirst().vector();
        if (vector.length != DIMENSION) {
            throw new IllegalStateException("Embedding model " + model.modelName() + " returned "
                    + vector.length + " dimensions, but knowledge_chunks.embedding expects " + DIMENSION);
        }
        return vector;
    }
}
