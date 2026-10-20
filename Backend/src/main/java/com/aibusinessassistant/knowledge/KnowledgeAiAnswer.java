package com.aibusinessassistant.knowledge;

import java.util.List;

/**
 * The chat model's answer to a knowledge-base question; citations are the ids of the chunks it used.
 */
public record KnowledgeAiAnswer(String answer, List<String> citations) {
}
