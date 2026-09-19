package com.aibusinessassistant.chat.memory;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "chat_memory_state")
public class ChatMemoryState {

    @Id
    @Column(name = "conversation_id")
    public UUID conversationId;

    @Column(name = "messages_json", nullable = false, columnDefinition = "TEXT")
    public String messagesJson;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;
}
