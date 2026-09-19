package com.aibusinessassistant.chat.memory;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class ChatMemoryStateRepository implements PanacheRepositoryBase<ChatMemoryState, UUID> {
}
