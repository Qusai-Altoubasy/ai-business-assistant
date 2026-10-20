package com.aibusinessassistant.knowledge.dto;

/**
 * A knowledge-base chunk that supports an answer. ruleId is null for a file's preamble.
 */
public record KnowledgeSourceDTO(String source, String section, String ruleId) {
}
