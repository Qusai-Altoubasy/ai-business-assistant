package com.aibusinessassistant.knowledge;

/**
 * One paragraph of a knowledge-base file, as produced by DocumentChunker and stored in knowledge_chunks.
 */
public record DocumentChunk(String source, String title, String section, String ruleId, int chunkIndex,
                            String content, String contentHash) {
}
