package com.aibusinessassistant.chat.history;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.aibusinessassistant.chat.dto.BusinessAnalysisDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class ConversationHistoryService {

    private final ConversationRepository conversations;
    private final ConversationMessageRepository messages;
    private final ObjectMapper objectMapper;

    @Transactional
    public void recordSuccessfulExchange(UUID conversationId, String query, BusinessAnalysisDTO response) {
        Conversation conversation = findOrCreateConversation(conversationId);
        OffsetDateTime now = OffsetDateTime.now();
        conversation.setUpdatedAt(now);

        persistMessage(conversation, ChatRole.USER, query, now);
        persistMessage(conversation, ChatRole.ASSISTANT, responseJson(response), now);
    }

    @Transactional
    public Conversation findOrCreateConversation(UUID conversationId) {
        Conversation conversation = conversations.findById(conversationId);
        if (conversation != null) {
            return conversation;
        }

        OffsetDateTime now = OffsetDateTime.now();
        conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        conversations.persist(conversation);
        return conversation;
    }

    private void persistMessage(Conversation conversation, ChatRole role, String content, OffsetDateTime createdAt) {
        ConversationMessage message = new ConversationMessage();
        message.setConversation(conversation);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(createdAt);
        messages.persist(message);
    }

    private String responseJson(BusinessAnalysisDTO response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to persist the assistant response", exception);
        }
    }
}
