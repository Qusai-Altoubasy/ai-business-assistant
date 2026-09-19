package com.aibusinessassistant.chat.memory;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class TurnAwareChatMemoryProvider implements ChatMemoryProvider {

    private final ChatMemoryStore store;
    private final int maxTurns;

    @Inject
    public TurnAwareChatMemoryProvider(
            ChatMemoryStore store,
            @ConfigProperty(name = "app.chat.memory.max-turns", defaultValue = "10") int maxTurns) {
        this.store = store;
        this.maxTurns = maxTurns;
    }

    @Override
    public ChatMemory get(Object memoryId) {
        return new TurnAwareChatMemory(memoryId, maxTurns, store);
    }
}
