package com.aibusinessassistant.chat.memory;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.aibusinessassistant.chat.history.ConversationHistoryService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

/**
 * Persists only the active LangChain4j context window. Complete user and assistant history is stored separately.
 */
@ApplicationScoped
@RequiredArgsConstructor
public class PostgresChatMemoryStore implements ChatMemoryStore {

    private final ChatMemoryStateRepository states;
    private final ConversationHistoryService conversations;

    @Override
    @Transactional
    public List<ChatMessage> getMessages(Object memoryId) {
        ChatMemoryState state = states.findById(asConversationId(memoryId));
        return state == null ? List.of() : ChatMessageDeserializer.messagesFromJson(state.getMessagesJson());
    }

    @Override
    @Transactional
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        UUID conversationId = asConversationId(memoryId);
        conversations.findOrCreateConversation(conversationId);

        ChatMemoryState state = states.findById(conversationId);

        String messagesJson = ChatMessageSerializer.messagesToJson(messages);
        OffsetDateTime now = OffsetDateTime.now();

        if (state == null) {
            state = new ChatMemoryState();
            state.setConversationId(conversationId);
            state.setMessagesJson(messagesJson);
            state.setUpdatedAt(now);

            states.persist(state);
            return;
        }
        state.setMessagesJson(messagesJson);
        state.setUpdatedAt(now);
    }

    @Override
    @Transactional
    public void deleteMessages(Object memoryId) {
        // LangChain4j eviction clears active context only. It must not remove persistent conversation history.
        states.deleteById(asConversationId(memoryId));
    }

    private static UUID asConversationId(Object memoryId) {
        if (memoryId == null || memoryId.toString().isBlank()) {
            throw new IllegalArgumentException("conversationId is required");
        }
        if (memoryId instanceof UUID conversationId) {
            return conversationId;
        }
        try {
            return UUID.fromString(memoryId.toString());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("conversationId must be a UUID", exception);
        }
    }
}
