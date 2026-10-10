package com.aibusinessassistant.chat.ai;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class AiProviderSelector {

    private final GeminiBusinessAnalysisService gemini;
    private final OllamaBusinessAnalysisService ollama;

    public BusinessAnalysisService select(AiProvider provider) {
        return switch (provider) {
            case GEMINI -> gemini;
            case OLLAMA -> ollama;
        };
    }
}
