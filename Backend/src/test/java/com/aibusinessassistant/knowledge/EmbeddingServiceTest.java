package com.aibusinessassistant.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.request.EmbeddingInputType;
import dev.langchain4j.model.embedding.request.EmbeddingParameter;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.request.EmbeddingRequestParameters;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;

class EmbeddingServiceTest {

    @Test
    void documentFromInventoryPolicyIsEmbeddedWith768Dimensions() throws IOException {
        String sentence = inventoryPolicyRule("INV-2");
        RecordingEmbeddingModel model = new RecordingEmbeddingModel(EmbeddingService.DIMENSION);

        float[] vector = new EmbeddingService(model).embedDocument(sentence);

        assertEquals(EmbeddingService.DIMENSION, vector.length);
        assertEquals(1, model.requests.size());
        EmbeddingRequest request = model.requests.get(0);
        assertEquals(1, request.inputs().size());
        assertEquals(sentence, request.inputs().get(0).text());
        assertEquals(EmbeddingInputType.DOCUMENT, request.inputType());
    }

    @Test
    void questionIsEmbeddedByTheSameModelAsAQuery() {
        RecordingEmbeddingModel model = new RecordingEmbeddingModel(EmbeddingService.DIMENSION);
        EmbeddingService service = new EmbeddingService(model);

        service.embedDocument("A product is OUT_OF_STOCK when stock_quantity = 0.");
        float[] vector = service.embedQuery("Which products are out of stock?");

        assertEquals(EmbeddingService.DIMENSION, vector.length);
        assertEquals(2, model.requests.size());
        assertEquals(EmbeddingInputType.DOCUMENT, model.requests.get(0).inputType());
        assertEquals(EmbeddingInputType.QUERY, model.requests.get(1).inputType());
        assertEquals("Which products are out of stock?", model.requests.get(1).inputs().get(0).text());
        assertEquals("recording-model", service.modelName());
    }

    @Test
    void vectorWithAnotherDimensionIsRejected() {
        EmbeddingService service = new EmbeddingService(new RecordingEmbeddingModel(3072));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.embedDocument("A product is OUT_OF_STOCK when stock_quantity = 0."));

        assertTrue(failure.getMessage().contains("3072"), failure.getMessage());
        assertTrue(failure.getMessage().contains("768"), failure.getMessage());
    }

    // Reads one rule's sentence, without its "**INV-n**" label, from the packaged knowledge base.
    private static String inventoryPolicyRule(String ruleId) throws IOException {
        String label = "**" + ruleId + "** ";
        try (InputStream policy = EmbeddingServiceTest.class.getResourceAsStream("/knowledge-base/inventory-policy.md")) {
            return new String(policy.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> line.startsWith(label))
                    .map(line -> line.substring(label.length()))
                    .findFirst()
                    .orElseThrow();
        }
    }

    // Returns a vector of the given size for every input and keeps each request it receives.
    private static class RecordingEmbeddingModel implements EmbeddingModel {

        private final int dimension;
        private final List<EmbeddingRequest> requests = new ArrayList<>();

        RecordingEmbeddingModel(int dimension) {
            this.dimension = dimension;
        }

        @Override
        public Set<EmbeddingParameter<?>> supportedParameters() {
            return Set.of(EmbeddingRequestParameters.INPUT_TYPE);
        }

        @Override
        public EmbeddingResponse doEmbed(EmbeddingRequest request) {
            requests.add(request);
            List<Embedding> embeddings = request.inputs().stream()
                    .map(input -> Embedding.from(new float[dimension]))
                    .toList();
            return EmbeddingResponse.builder().embeddings(embeddings).build();
        }

        @Override
        public String modelName() {
            return "recording-model";
        }
    }
}
