package com.aibusinessassistant.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@QuarkusTest
class ConversationProviderMigrationTest {

    @Inject
    EntityManager entityManager;

    @Test
    @TestTransaction
    void migrationBackfillsLegacyConversationsWithGemini() throws IOException {
        // DDL and the separate schema roll back with the test transaction.
        String schema = "provider_migration_" + UUID.randomUUID().toString().replace("-", "");
        entityManager.createNativeQuery("CREATE SCHEMA " + schema).executeUpdate();
        entityManager.createNativeQuery("SET LOCAL search_path TO " + schema).executeUpdate();
        runMigration("V3__add_persistent_chat_history.sql");
        UUID legacyId = UUID.randomUUID();
        entityManager.createNativeQuery("INSERT INTO conversations (id) VALUES (:id)")
                .setParameter("id", legacyId).executeUpdate();

        runMigration("V4__add_conversation_ai_provider.sql");

        assertEquals("GEMINI", entityManager.createNativeQuery(
                "SELECT ai_provider FROM conversations WHERE id = :id")
                .setParameter("id", legacyId).getSingleResult());
    }

    private void runMigration(String name) throws IOException {
        try (var input = getClass().getResourceAsStream("/db/migration/" + name)) {
            if (input == null) {
                throw new IOException("Missing migration " + name);
            }
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            for (String statement : sql.split(";")) {
                if (!statement.isBlank()) {
                    entityManager.createNativeQuery(statement).executeUpdate();
                }
            }
        }
    }
}
