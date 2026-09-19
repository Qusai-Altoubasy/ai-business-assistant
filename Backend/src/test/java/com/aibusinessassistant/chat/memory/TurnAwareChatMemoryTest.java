package com.aibusinessassistant.chat.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;

class TurnAwareChatMemoryTest {

    @Test
    void evictsWholeTurnsAndKeepsTheSystemMessage() {
        InMemoryChatMemoryStore store = new InMemoryChatMemoryStore();
        TurnAwareChatMemory memory = new TurnAwareChatMemory("conversation", 2, store);
        memory.add(SystemMessage.from("Business instructions"));
        memory.add(UserMessage.from("First question"));
        memory.add(new AiMessage("Calling a tool"));
        memory.add(ToolExecutionResultMessage.from("tool-1", "customer", "First result"));
        memory.add(new AiMessage("First answer"));
        memory.add(UserMessage.from("Second question"));
        memory.add(new AiMessage("Calling another tool"));
        memory.add(ToolExecutionResultMessage.from("tool-2", "sales", "Second result"));
        memory.add(new AiMessage("Second answer"));
        memory.add(UserMessage.from("Third question"));

        List<ChatMessage> active = memory.messages();
        assertEquals(6, active.size());
        assertInstanceOf(SystemMessage.class, active.get(0));
        assertEquals("Second question", ((UserMessage) active.get(1)).singleText());
        assertEquals("Calling another tool", ((AiMessage) active.get(2)).text());
        assertEquals("Second result", ((ToolExecutionResultMessage) active.get(3)).text());
        assertEquals("Second answer", ((AiMessage) active.get(4)).text());
        assertEquals("Third question", ((UserMessage) active.get(5)).singleText());
    }

    @Test
    void setAlsoEvictsWholeTurns() {
        TurnAwareChatMemory memory = new TurnAwareChatMemory("conversation", 1, new InMemoryChatMemoryStore());
        memory.set(List.of(
                SystemMessage.from("Instructions"),
                UserMessage.from("First"),
                new AiMessage("Calling a tool"),
                ToolExecutionResultMessage.from("tool-1", "customer", "Result"),
                UserMessage.from("Second"),
                new AiMessage("Second answer")));

        assertEquals(3, memory.messages().size());
        assertInstanceOf(SystemMessage.class, memory.messages().get(0));
        assertEquals("Second", ((UserMessage) memory.messages().get(1)).singleText());
    }

    @Test
    void rejectsPersistedMemoryThatStartsMidTurn() {
        InMemoryChatMemoryStore store = new InMemoryChatMemoryStore();
        store.updateMessages("conversation", List.of(
                SystemMessage.from("Instructions"),
                new AiMessage("Orphaned answer")));

        TurnAwareChatMemory memory = new TurnAwareChatMemory("conversation", 2, store);
        assertThrows(IllegalStateException.class, memory::messages);
    }
}
