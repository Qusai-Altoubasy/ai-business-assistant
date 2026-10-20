package com.aibusinessassistant.chat.ai;

import java.util.UUID;

import com.aibusinessassistant.chat.dto.BusinessAnalysisDTO;

import io.quarkiverse.langchain4j.runtime.aiservice.ChatEvent;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class OllamaBusinessAnalysisService implements BusinessAnalysisService {

    private final OllamaAiService delegate;

    // Knowledge-base sources are Gemini only; sources the model fills in from the response schema are discarded.
    @Override
    public BusinessAnalysisDTO chat(UUID conversationId, String query) {
        BusinessAnalysisDTO answer = delegate.chat(conversationId, query);
        return new BusinessAnalysisDTO(answer.summary(), answer.insights(), answer.recommendations());
    }

    @Override
    public Multi<ChatEvent> chatStream(UUID conversationId, String query) {
        return delegate.chatStream(conversationId, query);
    }
}
