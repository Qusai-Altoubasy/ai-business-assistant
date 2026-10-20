package com.aibusinessassistant.knowledge.dto;

import java.util.List;

public record KnowledgeAnswerDTO(String answer, List<KnowledgeSourceDTO> sources) {
}
