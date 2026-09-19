package com.aibusinessassistant.chat.history;

import java.util.List;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ConversationMessageRepository implements PanacheRepository<ConversationMessage> {

    public List<ConversationMessage> listByConversationId(UUID conversationId) {
        return list("conversation.id = ?1 order by createdAt, id", conversationId);
    }
}
