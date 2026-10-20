package com.aibusinessassistant.chat.ai;

import java.util.List;

/**
 * The Gemini chat model's business analysis; citations are the ids of the knowledge-base chunks it used, taken
 * from the searches of the same request, and empty for questions that use no policy.
 */
public record BusinessAnalysisAiAnswer(
        String summary,
        List<String> insights,
        List<String> recommendations,
        List<String> citations
) {
}
