package com.aibusinessassistant;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.aibusinessassistant.chat.dto.ChatRequestDTO;
import com.aibusinessassistant.knowledge.EmbeddingService;
import com.aibusinessassistant.knowledge.dto.KnowledgeQuestionDTO;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

// The datasource points at a port where no database listens, so every request fails on its first database call.
// /api/chat reads the conversation before the model is invoked; /api/knowledge/answer embeds the question with a
// fixed vector, so no request reaches Gemini, and then fails reading knowledge_chunks.
@QuarkusTest
@TestProfile(DatabaseUnavailableTest.UnreachableDatabase.class)
class DatabaseUnavailableTest {

    @Test
    void chatReturns500WithoutAnAnswer() {
        String body = given().contentType(ContentType.JSON).accept(ContentType.JSON)
                .body(new ChatRequestDTO(UUID.randomUUID().toString(), "Which products are low stock?"))
                .when().post("/api/chat")
                .then().statusCode(500)
                .extract().asString();

        assertFalse(body.contains("\"summary\""), body);
    }

    @Test
    void knowledgeAnswerReturns500WithoutAnAnswer() {
        String body = given().contentType(ContentType.JSON).accept(ContentType.JSON)
                .body(new KnowledgeQuestionDTO("How long does it take to receive a refund?"))
                .when().post("/api/knowledge/answer")
                .then().statusCode(500)
                .extract().asString();

        assertFalse(body.contains("\"answer\""), body);
    }

    // Starts without a database: no Dev Services container, no migrations and no schema validation.
    public static class UnreachableDatabase implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.datasource.devservices.enabled", "false",
                    "quarkus.datasource.jdbc.url", "jdbc:postgresql://127.0.0.1:1/unavailable",
                    "quarkus.datasource.username", "unavailable",
                    "quarkus.flyway.migrate-at-start", "false",
                    "quarkus.hibernate-orm.database.start-offline", "true",
                    "quarkus.hibernate-orm.schema-management.strategy", "none");
        }

        @Override
        public Set<Class<?>> getEnabledAlternatives() {
            return Set.of(FixedEmbeddingService.class);
        }
    }

    // Returns the same vector for every question; enabled only by UnreachableDatabase.
    @Alternative
    @ApplicationScoped
    public static class FixedEmbeddingService extends EmbeddingService {

        public FixedEmbeddingService() {
            super(null);
        }

        @Override
        public float[] embedQuery(String text) {
            float[] vector = new float[DIMENSION];
            vector[0] = 1;
            return vector;
        }

        @Override
        public String modelName() {
            return "fixed-test-model";
        }
    }
}
