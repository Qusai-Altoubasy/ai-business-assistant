package com.aibusinessassistant.chat.memory;

import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps complete user turns and a separate, unevictable system message.
 */
public final class TurnAwareChatMemory implements ChatMemory {

    private final Object id;
    private final int maxTurns;
    private final ChatMemoryStore store;

    public TurnAwareChatMemory(Object id, int maxTurns, ChatMemoryStore store) {
        if (id == null || store == null || maxTurns < 1) {
            throw new IllegalArgumentException("id, store, and a positive maxTurns are required");
        }
        this.id = id;
        this.maxTurns = maxTurns;
        this.store = store;
    }

    @Override
    public Object id() {
        return id;
    }

    @Override
    public void add(ChatMessage message) {
        List<ChatMessage> updated = new ArrayList<>(messages());
        if (message instanceof SystemMessage) {
            if (!updated.isEmpty() && updated.getFirst() instanceof SystemMessage) {
                updated.removeFirst();
            }
            updated.addFirst(message);
        } else {
            updated.add(message);
        }
        setMessages(updated);
    }

    @Override
    public void set(Iterable<ChatMessage> messages) {
        List<ChatMessage> updated = new ArrayList<>();
        messages.forEach(updated::add);
        setMessages(updated);
    }

    @Override
    public List<ChatMessage> messages() {
        List<ChatMessage> stored = new ArrayList<>(store.getMessages(id));
        validate(stored);
        return stored;
    }

    @Override
    public void clear() {
        store.deleteMessages(id);
    }

    private void setMessages(List<ChatMessage> updated) {
        validate(updated);
        int firstTurn = !updated.isEmpty() && updated.getFirst() instanceof SystemMessage ? 1 : 0;
        int turns = 0;
        for (int index = firstTurn; index < updated.size(); index++) {
            if (updated.get(index) instanceof UserMessage) {
                turns++;
            }
        }
        while (turns > maxTurns) {
            int nextTurn = firstTurn + 1;
            while (nextTurn < updated.size() && !(updated.get(nextTurn) instanceof UserMessage)) {
                nextTurn++;
            }
            updated.subList(firstTurn, nextTurn).clear();
            turns--;
        }
        store.updateMessages(id, updated);
    }

    private static void validate(List<ChatMessage> messages) {
        int firstTurn = !messages.isEmpty() && messages.getFirst() instanceof SystemMessage ? 1 : 0;
        if (firstTurn < messages.size() && !(messages.get(firstTurn) instanceof UserMessage)) {
            throw new IllegalStateException("Chat memory must begin with a UserMessage after the SystemMessage");
        }
        for (int index = firstTurn + 1; index < messages.size(); index++) {
            if (messages.get(index) instanceof SystemMessage) {
                throw new IllegalStateException("SystemMessage must be stored separately at the start of chat memory");
            }
            if (!(messages.get(index) instanceof UserMessage)
                    && !(messages.get(index) instanceof AiMessage)
                    && !(messages.get(index) instanceof ToolExecutionResultMessage)) {
                throw new IllegalStateException("Unsupported message type inside a user turn");
            }
        }
    }
}
