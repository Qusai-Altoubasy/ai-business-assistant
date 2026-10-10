package com.aibusinessassistant.chat.dto;

import com.aibusinessassistant.chat.ai.AiProvider;

public record ChatRequestDTO(String conversationId, String query, AiProvider provider) {
    public ChatRequestDTO(String conversationId, String query) {
        this(conversationId, query, null);
    }
}
