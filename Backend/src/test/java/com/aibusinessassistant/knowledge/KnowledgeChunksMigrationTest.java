package com.aibusinessassistant.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.hibernate.Session;
import org.junit.jupiter.api.Test;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@QuarkusTest
class KnowledgeChunksMigrationTest {

    private static final int DIMENSIONS = 768;

    private static final String INSERT_CHUNK = """
            INSERT INTO knowledge_chunks
                (source, section, rule_id, chunk_index, content, content_hash, embedding, embedding_model)
            VALUES (?, 'Test section', 'TST-1', ?, ?, 'test-hash', CAST(? AS vector), 'test-embedding-model')
            """;

    @Inject
    EntityManager entityManager;

    @Test
    @TestTransaction
    void vectorExtensionIsInstalled() {
        List<?> versions = entityManager.createNativeQuery(
                "SELECT extversion FROM pg_extension WHERE extname = 'vector'").getResultList();

        assertEquals(1, versions.size());
    }

    @Test
    @TestTransaction
    void embeddingColumnIsVector768() {
        Object type = entityManager.createNativeQuery("""
                SELECT format_type(a.atttypid, a.atttypmod)
                FROM pg_attribute a
                WHERE a.attrelid = to_regclass('knowledge_chunks')
                  AND a.attname = 'embedding'
                  AND NOT a.attisdropped
                """).getSingleResult();

        assertEquals("vector(768)", type);
    }

    @Test
    @TestTransaction
    void embeddingHasCosineHnswIndex() {
        Number indexes = (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM pg_index i
                JOIN pg_class ix ON ix.oid = i.indexrelid
                JOIN pg_am am ON am.oid = ix.relam
                JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = i.indkey[0]
                JOIN pg_opclass oc ON oc.oid = i.indclass[0]
                WHERE i.indrelid = to_regclass('knowledge_chunks')
                  AND am.amname = 'hnsw'
                  AND a.attname = 'embedding'
                  AND oc.opcname = 'vector_cosine_ops'
                """).getSingleResult();

        assertEquals(1, indexes.intValue());
    }

    @Test
    @TestTransaction
    void onlyVectorsWith768DimensionsAreAccepted() {
        String source = uniqueSource();
        insertChunk(source, 0, "768 dimensions", vector(DIMENSIONS, 0));

        SQLException failure = insertExpectingFailure(source, 1, vector(DIMENSIONS - 1, 0));

        assertNotNull(failure, "A 767-dimension vector must be rejected");
        assertEquals("22000", failure.getSQLState());
        assertTrue(failure.getMessage().contains("expected 768 dimensions"), failure.getMessage());
        assertEquals(1, countChunks(source));
        assertEquals("{}", entityManager.createNativeQuery(
                "SELECT CAST(metadata AS text) FROM knowledge_chunks WHERE source = :source")
                .setParameter("source", source).getSingleResult());
    }

    @Test
    @TestTransaction
    void sourceAndChunkIndexAreUnique() {
        String source = uniqueSource();
        insertChunk(source, 0, "first", vector(DIMENSIONS, 0));

        SQLException failure = insertExpectingFailure(source, 0, vector(DIMENSIONS, 1));

        assertNotNull(failure, "A second chunk with the same source and chunk_index must be rejected");
        assertEquals("23505", failure.getSQLState());
        assertTrue(failure.getMessage().contains("knowledge_chunks_source_chunk_index_unique"),
                failure.getMessage());
        assertEquals(1, countChunks(source));
    }

    @Test
    @TestTransaction
    void cosineDistanceOrdersChunksByClosestEmbedding() {
        String source = uniqueSource();
        // Against a query of [1, 0, ...]: same direction = 0, 45 degrees ~ 0.29, orthogonal = 1.
        insertChunk(source, 0, "orthogonal", vector(DIMENSIONS, 1));
        insertChunk(source, 1, "identical", vector(DIMENSIONS, 0));
        insertChunk(source, 2, "diagonal", vector(DIMENSIONS, 0, 1));
        // Exact ordering, independent of other rows the approximate HNSW scan could favor.
        entityManager.createNativeQuery("SET LOCAL enable_indexscan = off").executeUpdate();

        List<?> contents = entityManager.createNativeQuery("""
                SELECT content
                FROM knowledge_chunks
                WHERE source = :source
                ORDER BY embedding <=> CAST(:query AS vector)
                LIMIT 3
                """)
                .setParameter("source", source)
                .setParameter("query", vector(DIMENSIONS, 0))
                .getResultList();

        assertEquals(List.of("identical", "diagonal", "orthogonal"), contents);
    }

    private void insertChunk(String source, int chunkIndex, String content, String embedding) {
        entityManager.createNativeQuery(INSERT_CHUNK)
                .setParameter(1, source)
                .setParameter(2, chunkIndex)
                .setParameter(3, content)
                .setParameter(4, embedding)
                .executeUpdate();
    }

    // A failed statement aborts the PostgreSQL transaction, so the insert runs in a savepoint
    // over plain JDBC; rolling back to it keeps the test transaction usable for later checks.
    private SQLException insertExpectingFailure(String source, int chunkIndex, String embedding) {
        return entityManager.unwrap(Session.class).doReturningWork(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SAVEPOINT expected_failure");
            }
            try (PreparedStatement insert = connection.prepareStatement(INSERT_CHUNK)) {
                insert.setString(1, source);
                insert.setInt(2, chunkIndex);
                insert.setString(3, "expected to fail");
                insert.setString(4, embedding);
                insert.executeUpdate();
                return null;
            } catch (SQLException expected) {
                return expected;
            } finally {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("ROLLBACK TO SAVEPOINT expected_failure");
                }
            }
        });
    }

    private long countChunks(String source) {
        return ((Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM knowledge_chunks WHERE source = :source")
                .setParameter("source", source).getSingleResult()).longValue();
    }

    private static String uniqueSource() {
        return "migration-test-" + UUID.randomUUID() + ".md";
    }

    // Builds a pgvector literal with 1 at the given positions and 0 elsewhere.
    private static String vector(int dimensions, int... onePositions) {
        double[] values = new double[dimensions];
        for (int position : onePositions) {
            values[position] = 1;
        }
        return Arrays.stream(values)
                .mapToObj(value -> value == 1 ? "1" : "0")
                .collect(Collectors.joining(",", "[", "]"));
    }
}
