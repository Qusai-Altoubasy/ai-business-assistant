package com.aibusinessassistant.knowledge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.langchain4j.model.embedding.request.EmbeddingInputType;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import io.quarkus.panache.common.Sort;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

// Each test builds its own indexer around a fake embedding model, so no request reaches Gemini.
// The application's indexer bean does not index at startup in the test profile.
@QuarkusTest
class KnowledgeBaseIndexerTest {

    private static final String INVENTORY = "inventory-policy.md";
    private static final String REFUND = "refund-policy.md";
    private static final String CUSTOMER = "customer-policy.md";
    private static final String MODEL = "test-embedding-model";

    @Inject
    KnowledgeChunkRepository repository;

    @Inject
    EntityManager entityManager;

    private final DocumentChunker chunker = new DocumentChunker();

    @Test
    @TestTransaction
    void indexingInventoryPolicyStoresEveryChunk() {
        RecordingEmbeddingModel model = new RecordingEmbeddingModel(EmbeddingService.DIMENSION, MODEL);
        String markdown = KnowledgeBaseIndexer.readFile(INVENTORY);

        indexer(model).index(Map.of(INVENTORY, markdown));

        List<DocumentChunk> chunks = chunker.chunk(INVENTORY, markdown);
        assertEquals(14, chunks.size());
        assertEquals(expectedRows(chunks, MODEL), storedRows(INVENTORY));
        assertEquals(14L, ((Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM knowledge_chunks WHERE source = ? AND vector_dims(embedding) = 768")
                .setParameter(1, INVENTORY).getSingleResult()).longValue());
        assertEquals(14, model.requests().size());
        model.requests().forEach(request -> assertEquals(EmbeddingInputType.DOCUMENT, request.inputType()));
        assertEquals(chunks.stream().map(DocumentChunk::content).toList(), requestTexts(model.requests()));
    }

    @Test
    @TestTransaction
    void indexedChunksAreReadThroughTheEntity() {
        String markdown = KnowledgeBaseIndexer.readFile(INVENTORY);
        indexer(new RecordingEmbeddingModel(EmbeddingService.DIMENSION, MODEL)).index(Map.of(INVENTORY, markdown));

        List<KnowledgeChunk> stored = repository.list("source", Sort.by("chunkIndex"), INVENTORY);

        assertEquals(expectedRows(chunker.chunk(INVENTORY, markdown), MODEL), stored.stream()
                .map(chunk -> new Row(chunk.getChunkIndex(), chunk.getSection(), chunk.getRuleId(), chunk.getContent(),
                        chunk.getContentHash(), chunk.getEmbeddingModel(), (String) chunk.getMetadata().get("title")))
                .toList());
        float[] expectedVector = new float[EmbeddingService.DIMENSION];
        expectedVector[0] = 1;
        stored.forEach(chunk -> assertArrayEquals(expectedVector, chunk.getEmbedding()));
    }

    @Test
    @TestTransaction
    void indexingUnchangedFileAgainMakesNoEmbeddingCallsAndNoChanges() {
        RecordingEmbeddingModel model = new RecordingEmbeddingModel(EmbeddingService.DIMENSION, MODEL);
        KnowledgeBaseIndexer indexer = indexer(model);
        Map<String, String> documents = Map.of(INVENTORY, KnowledgeBaseIndexer.readFile(INVENTORY));
        indexer.index(documents);
        List<Object> idsAfterFirstRun = ids(INVENTORY);

        indexer.index(documents);

        assertEquals(14, model.requests().size());
        assertEquals(idsAfterFirstRun, ids(INVENTORY));
        assertEquals(14, idsAfterFirstRun.size());
    }

    @Test
    @TestTransaction
    void changedRuleTextReembedsOnlyThatChunk() {
        RecordingEmbeddingModel model = new RecordingEmbeddingModel(EmbeddingService.DIMENSION, MODEL);
        KnowledgeBaseIndexer indexer = indexer(model);
        String original = KnowledgeBaseIndexer.readFile(INVENTORY);
        indexer.index(Map.of(INVENTORY, original));
        List<Row> before = storedRows(INVENTORY);
        List<Object> ids = ids(INVENTORY);

        String changed = original.replace("is LOW when it has 11 to 20 units in stock.",
                "is LOW when it has 11 to 20 units on hand.");
        assertNotEquals(original, changed);
        indexer.index(Map.of(INVENTORY, changed));

        assertEquals(15, model.requests().size());
        assertEquals(chunker.chunk(INVENTORY, changed).get(4).content(), requestTexts(model.requests()).get(14));
        List<Row> after = storedRows(INVENTORY);
        assertEquals(expectedRows(chunker.chunk(INVENTORY, changed), MODEL), after);
        for (int index = 0; index < after.size(); index++) {
            if (index == 4) {
                assertNotEquals(before.get(index).content(), after.get(index).content());
                assertNotEquals(before.get(index).contentHash(), after.get(index).contentHash());
            } else {
                assertEquals(before.get(index), after.get(index));
            }
        }
        assertEquals(ids, ids(INVENTORY));
    }

    @Test
    @TestTransaction
    void removingARuleInTheMiddleReembedsShiftedChunksAndDeletesTheLastRow() {
        RecordingEmbeddingModel model = new RecordingEmbeddingModel(EmbeddingService.DIMENSION, MODEL);
        KnowledgeBaseIndexer indexer = indexer(model);
        String original = KnowledgeBaseIndexer.readFile(INVENTORY);
        indexer.index(Map.of(INVENTORY, original));

        String inv5 = original.lines().filter(line -> line.startsWith("**INV-5**")).findFirst().orElseThrow();
        String withoutInv5 = original.replace(inv5 + "\n\n", "");
        assertNotEquals(original, withoutInv5);
        indexer.index(Map.of(INVENTORY, withoutInv5));

        // INV-6 to INV-13 move from indexes 6..13 to 5..12; index 13 is left over.
        assertEquals(14 + 8, model.requests().size());
        List<DocumentChunk> chunks = chunker.chunk(INVENTORY, withoutInv5);
        assertEquals(13, chunks.size());
        assertEquals(expectedRows(chunks, MODEL), storedRows(INVENTORY));
    }

    @Test
    @TestTransaction
    void removingAFileFromTheMapDeletesItsRows() {
        RecordingEmbeddingModel model = new RecordingEmbeddingModel(EmbeddingService.DIMENSION, MODEL);
        KnowledgeBaseIndexer indexer = indexer(model);
        Map<String, String> both = new LinkedHashMap<>();
        both.put(INVENTORY, KnowledgeBaseIndexer.readFile(INVENTORY));
        both.put(REFUND, KnowledgeBaseIndexer.readFile(REFUND));
        indexer.index(both);
        assertEquals(15, storedRows(REFUND).size());

        indexer.index(Map.of(INVENTORY, both.get(INVENTORY)));

        assertEquals(List.of(), storedRows(REFUND));
        assertEquals(14, storedRows(INVENTORY).size());
        assertEquals(14 + 15, model.requests().size());
    }

    @Test
    @TestTransaction
    void anotherEmbeddingModelReembedsEveryChunk() {
        String markdown = KnowledgeBaseIndexer.readFile(INVENTORY);
        indexer(new RecordingEmbeddingModel(EmbeddingService.DIMENSION, "old-model")).index(Map.of(INVENTORY, markdown));
        List<Object> ids = ids(INVENTORY);

        RecordingEmbeddingModel newModel = new RecordingEmbeddingModel(EmbeddingService.DIMENSION, "new-model");
        indexer(newModel).index(Map.of(INVENTORY, markdown));

        assertEquals(14, newModel.requests().size());
        assertEquals(expectedRows(chunker.chunk(INVENTORY, markdown), "new-model"), storedRows(INVENTORY));
        assertEquals(ids, ids(INVENTORY));
    }

    @Test
    @TestTransaction
    void failedEmbeddingCallLeavesThePreviousVersionStored() {
        String original = KnowledgeBaseIndexer.readFile(INVENTORY);
        indexer(new RecordingEmbeddingModel(EmbeddingService.DIMENSION, MODEL)).index(Map.of(INVENTORY, original));
        List<Row> before = storedRows(INVENTORY);
        List<Object> ids = ids(INVENTORY);

        // A new title changes the content of every chunk, so all 14 need embedding again.
        String changed = original.replace("# Inventory Policy", "# Inventory Policy (revised)");
        RecordingEmbeddingModel failing = new RecordingEmbeddingModel(EmbeddingService.DIMENSION, MODEL).failOnCall(5);
        KnowledgeBaseIndexer indexer = indexer(failing);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> indexer.index(Map.of(INVENTORY, changed)));

        assertEquals("Could not index knowledge-base file " + INVENTORY, failure.getMessage());
        assertEquals(5, failing.requests().size());
        assertEquals(before, storedRows(INVENTORY));
        assertEquals(ids, ids(INVENTORY));
    }

    @Test
    @TestTransaction
    void removingTheLastRuleDeletesItsRowWithoutEmbedding() {
        RecordingEmbeddingModel model = new RecordingEmbeddingModel(EmbeddingService.DIMENSION, MODEL);
        KnowledgeBaseIndexer indexer = indexer(model);
        String original = KnowledgeBaseIndexer.readFile(INVENTORY);
        indexer.index(Map.of(INVENTORY, original));
        List<Object> ids = ids(INVENTORY);

        String inv13 = original.lines().filter(line -> line.startsWith("**INV-13**")).findFirst().orElseThrow();
        String withoutInv13 = original.replace("\n\n" + inv13, "");
        assertNotEquals(original, withoutInv13);
        indexer.index(Map.of(INVENTORY, withoutInv13));

        // No chunk changes, so only the shorter chunk count triggers the write that removes index 13.
        assertEquals(14, model.requests().size());
        assertEquals(expectedRows(chunker.chunk(INVENTORY, withoutInv13), MODEL), storedRows(INVENTORY));
        assertEquals(ids.subList(0, 13), ids(INVENTORY));
    }

    @Test
    @TestTransaction
    void failedSourceStopsLaterSourcesAndKeepsRowsOfSourcesMissingFromTheMap() {
        indexer(new RecordingEmbeddingModel(EmbeddingService.DIMENSION, MODEL))
                .index(Map.of(REFUND, KnowledgeBaseIndexer.readFile(REFUND)));
        List<Row> refundRows = storedRows(REFUND);

        RecordingEmbeddingModel failing = new RecordingEmbeddingModel(EmbeddingService.DIMENSION, MODEL).failOnCall(1);
        Map<String, String> documents = new LinkedHashMap<>();
        documents.put(INVENTORY, KnowledgeBaseIndexer.readFile(INVENTORY));
        documents.put(CUSTOMER, KnowledgeBaseIndexer.readFile(CUSTOMER));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> indexer(failing).index(documents));

        assertEquals("Could not index knowledge-base file " + INVENTORY, failure.getMessage());
        assertEquals(1, failing.requests().size());
        assertEquals(List.of(), storedRows(INVENTORY));
        assertEquals(List.of(), storedRows(CUSTOMER));
        assertEquals(refundRows, storedRows(REFUND));
    }

    @Test
    @TestTransaction
    void startupIndexesEveryKnowledgeBaseFileWhenEnabled() {
        RecordingEmbeddingModel model = new RecordingEmbeddingModel(EmbeddingService.DIMENSION, MODEL);

        indexer(model, true).onStart(new StartupEvent());

        assertEquals(11 + 14 + 15, model.requests().size());
        for (String file : KnowledgeBaseIndexer.FILES) {
            assertEquals(expectedRows(chunker.chunk(file, KnowledgeBaseIndexer.readFile(file)), MODEL),
                    storedRows(file));
        }
    }

    @Test
    @TestTransaction
    void startupDoesNotIndexWhenDisabled() {
        RecordingEmbeddingModel model = new RecordingEmbeddingModel(EmbeddingService.DIMENSION, MODEL);

        indexer(model, false).onStart(new StartupEvent());

        assertEquals(0, model.requests().size());
        KnowledgeBaseIndexer.FILES.forEach(file -> assertEquals(List.of(), storedRows(file)));
    }

    @Test
    @TestTransaction
    void failedStartupIndexingDoesNotStopTheApplication() {
        RecordingEmbeddingModel failing = new RecordingEmbeddingModel(EmbeddingService.DIMENSION, MODEL).failOnCall(1);

        assertDoesNotThrow(() -> indexer(failing, true).onStart(new StartupEvent()));

        assertEquals(1, failing.requests().size());
        KnowledgeBaseIndexer.FILES.forEach(file -> assertEquals(List.of(), storedRows(file)));
    }

    @Test
    @TestTransaction
    void emptySourceListIsRejectedAndNoRowIsDeleted() {
        KnowledgeBaseIndexer indexer = indexer(new RecordingEmbeddingModel(EmbeddingService.DIMENSION, MODEL));
        indexer.index(Map.of(INVENTORY, KnowledgeBaseIndexer.readFile(INVENTORY)));
        List<Row> before = storedRows(INVENTORY);

        assertThrows(IllegalArgumentException.class, () -> repository.deleteSourcesNotIn(List.of()));
        assertThrows(IllegalArgumentException.class, () -> indexer.index(Map.of()));

        assertEquals(before, storedRows(INVENTORY));
    }

    private KnowledgeBaseIndexer indexer(RecordingEmbeddingModel model) {
        return indexer(model, false);
    }

    private KnowledgeBaseIndexer indexer(RecordingEmbeddingModel model, boolean indexAtStartup) {
        return new KnowledgeBaseIndexer(new EmbeddingService(model), repository, indexAtStartup);
    }

    private record Row(int chunkIndex, String section, String ruleId, String content, String contentHash,
                       String embeddingModel, String title) {
    }

    private static List<Row> expectedRows(List<DocumentChunk> chunks, String model) {
        return chunks.stream()
                .map(chunk -> new Row(chunk.chunkIndex(), chunk.section(), chunk.ruleId(), chunk.content(),
                        chunk.contentHash(), model, chunk.title()))
                .toList();
    }

    private List<Row> storedRows(String source) {
        List<?> rows = entityManager.createNativeQuery("""
                SELECT chunk_index, section, rule_id, content, content_hash, embedding_model, metadata ->> 'title'
                FROM knowledge_chunks
                WHERE source = ?
                ORDER BY chunk_index
                """).setParameter(1, source).getResultList();
        return rows.stream()
                .map(Object[].class::cast)
                .map(row -> new Row(((Number) row[0]).intValue(), (String) row[1], (String) row[2], (String) row[3],
                        (String) row[4], (String) row[5], (String) row[6]))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<Object> ids(String source) {
        return entityManager.createNativeQuery("SELECT id FROM knowledge_chunks WHERE source = ? ORDER BY chunk_index")
                .setParameter(1, source).getResultList();
    }

    private static List<String> requestTexts(List<EmbeddingRequest> requests) {
        return requests.stream().map(request -> request.inputs().getFirst().text()).toList();
    }
}
