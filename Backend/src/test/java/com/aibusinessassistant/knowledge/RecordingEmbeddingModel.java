package com.aibusinessassistant.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.request.EmbeddingParameter;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.request.EmbeddingRequestParameters;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;

// Returns a vector of the given size for every input and keeps each request it receives.
// It can be told to fail on the Nth request, counting from 1.
class RecordingEmbeddingModel implements EmbeddingModel {

    private final int dimension;
    private final String modelName;
    private final List<EmbeddingRequest> requests = new ArrayList<>();
    private int failOnCall;

    RecordingEmbeddingModel(int dimension) {
        this(dimension, "recording-model");
    }

    RecordingEmbeddingModel(int dimension, String modelName) {
        this.dimension = dimension;
        this.modelName = modelName;
    }

    RecordingEmbeddingModel failOnCall(int call) {
        this.failOnCall = call;
        return this;
    }

    List<EmbeddingRequest> requests() {
        return requests;
    }

    @Override
    public Set<EmbeddingParameter<?>> supportedParameters() {
        return Set.of(EmbeddingRequestParameters.INPUT_TYPE);
    }

    @Override
    public EmbeddingResponse doEmbed(EmbeddingRequest request) {
        requests.add(request);
        if (requests.size() == failOnCall) {
            throw new IllegalStateException("Embedding request " + failOnCall + " failed");
        }
        List<Embedding> embeddings = request.inputs().stream()
                .map(input -> Embedding.from(unitVector()))
                .toList();
        return EmbeddingResponse.builder().embeddings(embeddings).build();
    }

    @Override
    public String modelName() {
        return modelName;
    }

    // A nonzero vector, so stored chunks have a defined cosine distance.
    private float[] unitVector() {
        float[] vector = new float[dimension];
        vector[0] = 1;
        return vector;
    }
}
