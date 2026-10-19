package com.aibusinessassistant.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.aibusinessassistant.knowledge.KnowledgeChunkRepository.EmbeddedChunk;
import dev.langchain4j.model.embedding.request.EmbeddingInputType;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

// Each test builds its own retriever around a fake embedding model, so no request reaches Gemini.
// The fake embeds every question as e0, so the stored vectors below fix the expected similarities.
// These tests prove the query, ranking, top-k and threshold, not that a question matches the right rule.
@QuarkusTest
class KnowledgeRetrieverTest {

    private static final String MODEL = "retriever-test-model";
    private static final String SOURCE = "retriever-test.md";
    private static final String QUESTION = "When is a product considered critical?";
    private static final double TOLERANCE = 1e-6;

    @Inject
    KnowledgeChunkRepository repository;

    @Test
    @TestTransaction
    void chunksAreOrderedBySimilarityWithTheirSourceSectionAndRuleId() {
        storeStandardChunks();

        List<RetrievedChunk> chunks = retriever(new RecordingEmbeddingModel(EmbeddingService.DIMENSION, MODEL), 10, -1)
                .retrieve(QUESTION);

        assertEquals(List.of("identical", "diagonal", "orthogonal", "opposite"), contents(chunks));
        assertChunk(chunks.get(0), "Same direction", "TST-1", "identical", 1);
        assertChunk(chunks.get(1), "45 degrees", "TST-2", "diagonal", Math.sqrt(0.5));
        assertChunk(chunks.get(2), "Right angle", "TST-3", "orthogonal", 0);
        assertChunk(chunks.get(3), "Test Policy", null, "opposite", -1);
    }

    @Test
    @TestTransaction
    void atMostTopKChunksAreReturned() {
        storeStandardChunks();

        List<RetrievedChunk> chunks = retriever(new RecordingEmbeddingModel(EmbeddingService.DIMENSION, MODEL), 2, -1)
                .retrieve(QUESTION);

        assertEquals(List.of("identical", "diagonal"), contents(chunks));
    }

    @Test
    @TestTransaction
    void chunksBelowMinSimilarityAreDropped() {
        storeStandardChunks();
        RecordingEmbeddingModel model = new RecordingEmbeddingModel(EmbeddingService.DIMENSION, MODEL);

        assertEquals(List.of("identical", "diagonal"), contents(retriever(model, 10, 0.5).retrieve(QUESTION)));
        assertEquals(List.of("identical"), contents(retriever(model, 10, 1).retrieve(QUESTION)));
    }

    @Test
    @TestTransaction
    void noChunkReachingMinSimilarityGivesAnEmptyList() {
        store(SOURCE, MODEL, chunk(SOURCE, 0, "Right angle", "TST-3", "orthogonal", unit(1)),
                chunk(SOURCE, 1, "Test Policy", null, "opposite", opposite()));

        List<RetrievedChunk> chunks = retriever(new RecordingEmbeddingModel(EmbeddingService.DIMENSION, MODEL), 10, 0.5)
                .retrieve(QUESTION);

        assertEquals(List.of(), chunks);
    }

    @Test
    @TestTransaction
    void chunksEmbeddedWithAnotherModelAreIgnored() {
        // Different sources, because (source, chunk_index) is unique whatever the model.
        store("other-model.md", "other-model", chunk("other-model.md", 0, "Same direction", "TST-1", "identical", unit(0)));
        store(SOURCE, MODEL, chunk(SOURCE, 0, "45 degrees", "TST-2", "diagonal", unit(0, 1)));

        List<RetrievedChunk> chunks = retriever(new RecordingEmbeddingModel(EmbeddingService.DIMENSION, MODEL), 10, -1)
                .retrieve(QUESTION);

        assertEquals(List.of("diagonal"), contents(chunks));
    }

    @Test
    @TestTransaction
    void blankOrMissingQuestionGivesAnEmptyListWithoutEmbedding() {
        storeStandardChunks();
        RecordingEmbeddingModel model = new RecordingEmbeddingModel(EmbeddingService.DIMENSION, MODEL);
        KnowledgeRetriever retriever = retriever(model, 10, -1);

        assertEquals(List.of(), retriever.retrieve(null));
        assertEquals(List.of(), retriever.retrieve(""));
        assertEquals(List.of(), retriever.retrieve("   "));
        assertTrue(model.requests().isEmpty());
    }

    @Test
    @TestTransaction
    void questionIsEmbeddedOnceAsAQuery() {
        storeStandardChunks();
        RecordingEmbeddingModel model = new RecordingEmbeddingModel(EmbeddingService.DIMENSION, MODEL);

        retriever(model, 10, -1).retrieve(QUESTION);

        assertEquals(1, model.requests().size());
        assertEquals(EmbeddingInputType.QUERY, model.requests().get(0).inputType());
        assertEquals(QUESTION, model.requests().get(0).inputs().get(0).text());
    }

    private KnowledgeRetriever retriever(RecordingEmbeddingModel model, int topK, double minSimilarity) {
        return new KnowledgeRetriever(new EmbeddingService(model), repository, topK, minSimilarity);
    }

    // Against the fake's question vector e0: e0 = 1, e0 + e1 = cos 45 degrees, e1 = 0, -e0 = -1.
    // Stored out of order, so the result order comes from the query.
    private void storeStandardChunks() {
        store(SOURCE, MODEL,
                chunk(SOURCE, 0, "Right angle", "TST-3", "orthogonal", unit(1)),
                chunk(SOURCE, 1, "Test Policy", null, "opposite", opposite()),
                chunk(SOURCE, 2, "Same direction", "TST-1", "identical", unit(0)),
                chunk(SOURCE, 3, "45 degrees", "TST-2", "diagonal", unit(0, 1)));
    }

    private void store(String source, String model, EmbeddedChunk... chunks) {
        repository.saveAndDeleteStale(source, List.of(chunks), model, chunks.length);
    }

    private static EmbeddedChunk chunk(String source, int chunkIndex, String section, String ruleId, String content,
                                       float[] vector) {
        return new EmbeddedChunk(new DocumentChunk(source, "Test Policy", section, ruleId, chunkIndex, content,
                "hash-" + chunkIndex), vector);
    }

    // A unit vector pointing between the given axes, for example unit(0, 1) = (e0 + e1) / sqrt(2).
    private static float[] unit(int... axes) {
        float[] vector = new float[EmbeddingService.DIMENSION];
        for (int axis : axes) {
            vector[axis] = (float) (1 / Math.sqrt(axes.length));
        }
        return vector;
    }

    // -e0, the direction opposite to the fake's question vector.
    private static float[] opposite() {
        float[] vector = new float[EmbeddingService.DIMENSION];
        vector[0] = -1;
        return vector;
    }

    private static void assertChunk(RetrievedChunk chunk, String section, String ruleId, String content,
                                    double similarity) {
        assertEquals(SOURCE, chunk.source());
        assertEquals(section, chunk.section());
        assertEquals(ruleId, chunk.ruleId());
        assertEquals(content, chunk.content());
        assertEquals(similarity, chunk.similarity(), TOLERANCE, content);
    }

    private static List<String> contents(List<RetrievedChunk> chunks) {
        return chunks.stream().map(RetrievedChunk::content).toList();
    }
}
