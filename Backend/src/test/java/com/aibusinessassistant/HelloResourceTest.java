package com.aibusinessassistant;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class HelloResourceTest {

    @Test
    void helloEndpointReturnsHelloWorld() {
        given()
          .when().get("/hello")
          .then()
             .statusCode(200)
             .contentType("text/plain")
             .body(is("Hello World"));
    }
}
