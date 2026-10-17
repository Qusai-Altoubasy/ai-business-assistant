package com.aibusinessassistant.knowledge;

import java.time.OffsetDateTime;
import java.util.Map;

import org.hibernate.annotations.Array;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Read-only mapping of knowledge_chunks. Rows are written only by KnowledgeChunkRepository.saveAndDeleteStale with
 * native SQL: standard JPA has no insert-or-update on (source, chunk_index), and Hibernate's ON CONFLICT
 * support for HQL inserts is still incubating.
 */
@Getter
@NoArgsConstructor
@Immutable
@Entity
@Table(name = "knowledge_chunks")
public class KnowledgeChunk {

    @Id
    private Long id;

    @Column(name = "source", nullable = false, columnDefinition = "TEXT")
    private String source;

    @Column(name = "section", nullable = false, columnDefinition = "TEXT")
    private String section;

    @Column(name = "rule_id", columnDefinition = "TEXT")
    private String ruleId;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "content_hash", nullable = false, columnDefinition = "TEXT")
    private String contentHash;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = EmbeddingService.DIMENSION)
    @Column(name = "embedding", nullable = false)
    private float[] embedding;

    @Column(name = "embedding_model", nullable = false, columnDefinition = "TEXT")
    private String embeddingModel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false)
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
