package com.aibusinessassistant.chat.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import com.aibusinessassistant.chat.history.ChatRole;
import com.aibusinessassistant.chat.history.ConversationHistoryService;
import com.aibusinessassistant.chat.history.ConversationMessageRepository;
import org.junit.jupiter.api.Test;

import com.aibusinessassistant.chat.dto.BusinessAnalysisDTO;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class ConversationMemoryPersistenceTest {

    @Inject
    ChatMemoryStore chatMemoryStore;

    @Inject
    ConversationHistoryService history;

    @Inject
    ConversationMessageRepository messages;

    @Test
    @TestTransaction
    void sameConversationRestoresItsMemoryAfterRecreatingTheWindow() {
        UUID conversationId = conversationId();
        TurnAwareChatMemory firstWindow = memory(conversationId);
        firstWindow.add(UserMessage.from("Show me customer 2 statistics."));
        firstWindow.add(new AiMessage("Customer 2 spent $753.00."));
        firstWindow.add(UserMessage.from("How much did they spend?"));

        TurnAwareChatMemory restoredWindow = memory(conversationId);
        List<ChatMessage> restoredMessages = restoredWindow.messages();

        assertEquals(3, restoredMessages.size());
        assertEquals("Show me customer 2 statistics.", ((UserMessage) restoredMessages.get(0)).singleText());
        assertEquals("Customer 2 spent $753.00.", ((AiMessage) restoredMessages.get(1)).text());
        assertEquals("How much did they spend?", ((UserMessage) restoredMessages.get(2)).singleText());
    }

    @Test
    @TestTransaction
    void conversationsRemainIsolated() {
        UUID conversationA = conversationId();
        UUID conversationB = conversationId();
        chatMemoryStore.updateMessages(conversationA, List.of(UserMessage.from("Customer 2 is Omar Ellis.")));
        chatMemoryStore.updateMessages(conversationB, List.of(UserMessage.from("Customer 3 is Lina Brooks.")));

        assertEquals("Customer 2 is Omar Ellis.", ((UserMessage) chatMemoryStore.getMessages(conversationA).getFirst())
                .singleText());
        assertEquals("Customer 3 is Lina Brooks.", ((UserMessage) chatMemoryStore.getMessages(conversationB).getFirst())
                .singleText());
    }

    @Test
    @TestTransaction
    void fullHistoryRemainsWhenTheActiveWindowEvictsOlderMessages() {
        UUID conversationId = conversationId();
        for (int index = 0; index < 6; index++) {
            history.recordSuccessfulExchange(conversationId, "Question " + index,
                    new BusinessAnalysisDTO("Answer " + index, List.of(), List.of()));
        }

        TurnAwareChatMemory window = memory(conversationId);
        for (int index = 0; index < 12; index++) {
            window.add(UserMessage.from("Context " + index));
        }

        assertEquals(12, messages.listByConversationId(conversationId).size());
        assertEquals(ChatRole.USER, messages.listByConversationId(conversationId).getFirst().getRole());
        assertEquals(ChatRole.ASSISTANT, messages.listByConversationId(conversationId).get(1).getRole());
        List<ChatMessage> activeMessages = memory(conversationId).messages();
        assertEquals(10, activeMessages.size());
        assertEquals("Context 2", ((UserMessage) activeMessages.getFirst()).singleText());
        assertEquals("Context 11", ((UserMessage) activeMessages.getLast()).singleText());
    }

    @Test
    void invalidConversationIdIsRejected() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> chatMemoryStore.getMessages("not-a-uuid"));
        assertTrue(exception.getMessage().contains("conversationId"));
    }

    private TurnAwareChatMemory memory(UUID conversationId) {
        return new TurnAwareChatMemory(conversationId, 10, chatMemoryStore);
    }

    private static UUID conversationId() {
        return UUID.randomUUID();
    }
}
