package com.aibusinessassistant.chat.memory;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "chat_memory_state")
public class ChatMemoryState {

    @Id
    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "messages_json", nullable = false, columnDefinition = "TEXT")
    private String messagesJson;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
