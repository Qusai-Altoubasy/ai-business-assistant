package com.aibusinessassistant.chat.history;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "chat_messages")
public class ConversationMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    public Conversation conversation;

    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    public ChatRole role;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    public String content;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt;
}
