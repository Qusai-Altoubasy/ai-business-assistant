package com.aibusinessassistant.knowledge;

import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Reads and deletes knowledge_chunks through the KnowledgeChunk entity. Writes use native SQL: standard
 * JPA has no insert-or-update on (source, chunk_index), and Hibernate's ON CONFLICT support for HQL
 * inserts is still incubating.
 */
@ApplicationScoped
public class KnowledgeChunkRepository implements PanacheRepository<KnowledgeChunk> {

    // Rows are matched on (source, chunk_index), so re-indexing a file updates its rows in place.
    private static final String UPSERT_CHUNK = """
            INSERT INTO knowledge_chunks
                (source, section, rule_id, chunk_index, content, content_hash, embedding, embedding_model, metadata)
            VALUES (?, ?, CAST(? AS text), ?, ?, ?, CAST(? AS vector), ?, jsonb_build_object('title', CAST(? AS text)))
            ON CONFLICT (source, chunk_index) DO UPDATE SET
                section = EXCLUDED.section,
                rule_id = EXCLUDED.rule_id,
                content = EXCLUDED.content,
                content_hash = EXCLUDED.content_hash,
                embedding = EXCLUDED.embedding,
                embedding_model = EXCLUDED.embedding_model,
                metadata = EXCLUDED.metadata,
                updated_at = now()
            """;

    public record StoredChunk(int chunkIndex, String contentHash, String embeddingModel) {
    }

    public record EmbeddedChunk(DocumentChunk chunk, float[] vector) {
    }

    @Transactional
    public List<StoredChunk> findStored(String source) {
        // Projects only the compared columns, so the stored vectors are not loaded.
        return find("source", Sort.by("chunkIndex"), source).project(StoredChunk.class).list();
    }

    // Writes the changed chunks and removes rows past the file's last chunk, in one transaction per file.
    // The source is passed separately because rows can be removed when no chunk changed. Returns the removed rows.
    @Transactional
    public int saveAndDeleteStale(String source, List<EmbeddedChunk> changed, String model, int chunkCount) {
        for (EmbeddedChunk embedded : changed) {
            DocumentChunk chunk = embedded.chunk();
            getEntityManager().createNativeQuery(UPSERT_CHUNK)
                    .setParameter(1, source)
                    .setParameter(2, chunk.section())
                    .setParameter(3, chunk.ruleId())
                    .setParameter(4, chunk.chunkIndex())
                    .setParameter(5, chunk.content())
                    .setParameter(6, chunk.contentHash())
                    .setParameter(7, toVectorLiteral(embedded.vector()))
                    .setParameter(8, model)
                    .setParameter(9, chunk.title())
                    .executeUpdate();
        }
        return deleteChunksFrom(source, chunkCount);
    }

    // Removes the rows of a source from the given chunk index onwards, which no longer exist in its file.
    private int deleteChunksFrom(String source, int chunkIndex) {
        return getEntityManager()
                .createNativeQuery("DELETE FROM knowledge_chunks WHERE source = ? AND chunk_index >= ?")
                .setParameter(1, source)
                .setParameter(2, chunkIndex)
                .executeUpdate();
    }

    // An empty list would match every row, so it is rejected instead of emptying the table.
    @Transactional
    public void deleteSourcesNotIn(Collection<String> sources) {
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("No sources to keep; refusing to delete every knowledge chunk");
        }
        delete("source not in ?1", sources);
    }

    private static String toVectorLiteral(float[] vector) {
        StringJoiner literal = new StringJoiner(",", "[", "]");
        for (float value : vector) {
            literal.add(Float.toString(value));
        }
        return literal.toString();
    }
}
