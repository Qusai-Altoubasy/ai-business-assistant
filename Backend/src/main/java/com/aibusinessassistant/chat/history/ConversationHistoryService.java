package com.aibusinessassistant.chat.history;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.aibusinessassistant.chat.ai.AiProvider;
import com.aibusinessassistant.chat.dto.BusinessAnalysisDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class ConversationHistoryService {

    private final ConversationRepository conversations;
    private final ConversationMessageRepository messages;
    private final ObjectMapper objectMapper;
    private final AiProvider defaultProvider;

    @Inject
    public ConversationHistoryService(
            ConversationRepository conversations,
            ConversationMessageRepository messages,
            ObjectMapper objectMapper,
            @ConfigProperty(name = "app.ai.default-provider", defaultValue = "GEMINI") AiProvider defaultProvider) {
        this.conversations = conversations;
        this.messages = messages;
        this.objectMapper = objectMapper;
        this.defaultProvider = defaultProvider;
    }

    @Transactional
    public void recordSuccessfulExchange(UUID conversationId, String query, BusinessAnalysisDTO response) {
        recordSuccessfulTextExchange(conversationId, query, responseJson(response));
    }

    @Transactional
    public void recordSuccessfulTextExchange(UUID conversationId, String query, String response) {
        Conversation conversation = findOrCreateConversation(conversationId);
        OffsetDateTime now = OffsetDateTime.now();
        conversation.setUpdatedAt(now);

        persistMessage(conversation, ChatRole.USER, query, now);
        persistMessage(conversation, ChatRole.ASSISTANT, response, now);
    }

    @Transactional
    public Conversation findOrCreateConversation(UUID conversationId) {
        return findOrCreateConversation(conversationId, null);
    }

    @Transactional
    public Conversation findOrCreateConversation(UUID conversationId, AiProvider requestedProvider) {
        Conversation conversation = conversations.findById(conversationId);
        if (conversation != null) {
            if (requestedProvider != null && requestedProvider != conversation.getProvider()) {
                throw new WebApplicationException(
                        "Start a new conversation to change the provider.", 409);
            }
            return conversation;
        }

        AiProvider selectedProvider = requestedProvider != null ? requestedProvider : defaultProvider;
        OffsetDateTime now = OffsetDateTime.now();
        conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setProvider(selectedProvider);
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
