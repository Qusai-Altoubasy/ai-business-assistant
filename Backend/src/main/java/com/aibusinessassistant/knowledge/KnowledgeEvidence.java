package com.aibusinessassistant.knowledge;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.aibusinessassistant.knowledge.dto.KnowledgeSourceDTO;

/**
 * The chunks retrieved for a question, in retrieval order: the context a chat model reads and the sources its
 * citations map to. Each chunk has a stable id, its rule ID or "<source>#preamble", that does not depend on the
 * question, so citations from several retrievals cannot collide.
 */
public record KnowledgeEvidence(List<RetrievedChunk> chunks) {

    public KnowledgeEvidence {
        chunks = List.copyOf(chunks);
    }

    public boolean isEmpty() {
        return chunks.isEmpty();
    }

    // Relies on rule IDs being unique across the knowledge-base files.
    public static String chunkId(RetrievedChunk chunk) {
        return chunk.ruleId() != null ? chunk.ruleId() : chunk.source() + "#preamble";
    }

    // One <chunk> block per chunk, in retrieval order.
    public String format() {
        return chunks.stream()
                .map(chunk -> "<chunk id=\"" + chunkId(chunk) + "\" source=\"" + chunk.source()
                        + "\" section=\"" + chunk.section() + "\">\n" + chunk.content() + "\n</chunk>")
                .collect(Collectors.joining("\n\n"));
    }

    // Keeps only ids of chunks in this evidence, so an id the model invents is dropped; retrieval order, no duplicates.
    public List<KnowledgeSourceDTO> sourcesFor(Collection<String> citedIds) {
        if (citedIds == null || citedIds.isEmpty()) {
            return List.of();
        }
        Set<String> cited = new HashSet<>(citedIds);
        return chunks.stream()
                .filter(chunk -> cited.contains(chunkId(chunk)))
                .map(chunk -> new KnowledgeSourceDTO(chunk.source(), chunk.section(), chunk.ruleId()))
                .distinct()
                .toList();
    }
}
