package com.aibusinessassistant.knowledge;

/**
 * A knowledge-base chunk found for a question. similarity is 1 - cosine distance, from -1 to 1;
 * higher is closer. ruleId is null for a file's preamble.
 */
public record RetrievedChunk(String source, String section, String ruleId, String content, double similarity) {
}
