package com.aibusinessassistant.chat.dto;

import java.util.List;

import com.aibusinessassistant.knowledge.dto.KnowledgeSourceDTO;

/**
 * sources holds the knowledge-base chunks the analysis cites; it is empty when no policy was used and for
 * history stored before sources existed.
 */
public record BusinessAnalysisDTO(
        String summary,
        List<String> insights,
        List<String> recommendations,
        List<KnowledgeSourceDTO> sources
) {
    public BusinessAnalysisDTO {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public BusinessAnalysisDTO(String summary, List<String> insights, List<String> recommendations) {
        this(summary, insights, recommendations, List.of());
    }
}
