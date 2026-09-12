package com.aibusinessassistant.chat;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.aibusinessassistant.chat.ai.ChatService;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

@QuarkusTest
class ChatResourceTest {

    private static final String QUERY = "What can you help me with?";
    static final String AI_RESPONSE = "I can help with your business questions.";

    @Test
    void chatEndpointReturnsAiResponseAsJson() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"query\":\"" + QUERY + "\"}")
                .when().post("/api/chat")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("response", is(AI_RESPONSE));

        assertEquals(QUERY, TestChatService.lastQuery);
    }
}

@Alternative
@Priority(1)
@ApplicationScoped
class TestChatService implements ChatService {

    static volatile String lastQuery;

    @Override
    public String chat(String query) {
        lastQuery = query;
        return ChatResourceTest.AI_RESPONSE;
    }
}
