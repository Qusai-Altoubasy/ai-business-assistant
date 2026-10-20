package com.aibusinessassistant.knowledge;

import static io.restassured.RestAssured.given;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

// The request is rejected before KnowledgeAnswerService is called, which is the only path to the embedding and
// chat models, so neither model is called. Starting the application also registers KnowledgeAiService and
// validates the retriever's configuration.
@QuarkusTest
class KnowledgeResourceTest {

    @Test
    void missingBodyOrBlankQuestionIsRejected() {
        for (String body : List.of("", "null", "{}", "{\"question\":null}", "{\"question\":\"\"}",
                "{\"question\":\"   \"}")) {
            given()
                    .contentType(ContentType.JSON)
                    .body(body)
                    .when().post("/api/knowledge/answer")
                    .then().statusCode(400);
        }
    }
}
