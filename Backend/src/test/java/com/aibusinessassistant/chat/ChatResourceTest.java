package com.aibusinessassistant.chat;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.aibusinessassistant.chat.ai.BusinessAnalysisService;
import com.aibusinessassistant.chat.dto.BusinessAnalysisDTO;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

@QuarkusTest
class ChatResourceTest {

    private static final String QUERY = "What can you help me with?";
    private static final String CONVERSATION_ID = "1f5299c7-84a5-4a89-a5e4-1058debf4a31";
    static final String AI_RESPONSE = "I can help with your business questions.";
    static final List<String> INSIGHTS = List.of("Inventory and sales data are available.");
    static final List<String> RECOMMENDATIONS = List.of("Choose a product or sales period to review.");

    @Test
    void chatEndpointReturnsStructuredAnalysisAsJson() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"conversationId\":\"" + CONVERSATION_ID + "\",\"query\":\"" + QUERY + "\"}")
                .when().post("/api/chat")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("summary", is(AI_RESPONSE))
                .body("insights", is(INSIGHTS))
                .body("recommendations", is(RECOMMENDATIONS));

        assertEquals(QUERY, TestBusinessAnalysisService.lastQuery);
        assertEquals(UUID.fromString(CONVERSATION_ID), TestBusinessAnalysisService.lastConversationId);
    }

    @Test
    void chatEndpointRequiresConversationId() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"query\":\"" + QUERY + "\"}")
                .when().post("/api/chat")
                .then().statusCode(400);
    }

    @Test
    void chatEndpointRequiresUuidConversationId() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"conversationId\":\"conversation-123\",\"query\":\"" + QUERY + "\"}")
                .when().post("/api/chat")
                .then().statusCode(400);
    }
}

@Alternative
@Priority(1)
@ApplicationScoped
class TestBusinessAnalysisService implements BusinessAnalysisService {

    static volatile String lastQuery;
    static volatile UUID lastConversationId;

    @Override
    public BusinessAnalysisDTO chat(UUID conversationId, String query) {
        lastConversationId = conversationId;
        lastQuery = query;
        return new BusinessAnalysisDTO(
                ChatResourceTest.AI_RESPONSE,
                ChatResourceTest.INSIGHTS,
                ChatResourceTest.RECOMMENDATIONS
        );
    }
}
