package com.aibusinessassistant.chat.ai;

import java.util.UUID;

import com.aibusinessassistant.chat.dto.BusinessAnalysisDTO;

import io.quarkiverse.langchain4j.runtime.aiservice.ChatEvent;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class GeminiBusinessAnalysisService implements BusinessAnalysisService {

    private final GeminiAiService delegate;

    @Override
    public BusinessAnalysisDTO chat(UUID conversationId, String query) {
        return delegate.chat(conversationId, query);
    }

    @Override
    public Multi<ChatEvent> chatStream(UUID conversationId, String query) {
        return delegate.chatStream(conversationId, query);
    }
}
