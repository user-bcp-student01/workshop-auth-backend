package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class CorsConfigurationTest {
    private static final String LOCAL = "http://localhost:4200";
    private static final String FOREIGN = "https://untrusted.example";

    @Test
    void allowsLocalPreflightWithNecessaryHeaders() {
        given().header("Origin", LOCAL).header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,authorization")
                .options("/auth/login").then()
                .header("Access-Control-Allow-Origin", is(LOCAL))
                .header("Access-Control-Allow-Methods", containsString("POST"))
                .header("Access-Control-Allow-Headers", containsString("content-type"))
                .header("Access-Control-Allow-Headers", containsString("authorization"));
    }

    @Test
    void deniesForeignOrigin() {
        given().header("Origin", FOREIGN).header("Access-Control-Request-Method", "POST")
                .options("/auth/login").then().header("Access-Control-Allow-Origin", nullValue());
        given().header("Origin", FOREIGN).contentType("application/json")
                .body(Map.of("username", "student", "password", "Workshop123!"))
                .post("/auth/login").then().header("Access-Control-Allow-Origin", nullValue());
    }

    @Test
    void deniesUnnecessaryMethodsAndHeaders() {
        given().header("Origin", LOCAL).header("Access-Control-Request-Method", "DELETE")
                .options("/auth/login").then()
                .header("Access-Control-Allow-Methods", not(containsString("DELETE")));
        given().header("Origin", LOCAL).header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "x-not-allowed")
                .options("/auth/login").then()
                .header("Access-Control-Allow-Headers", not(containsString("x-not-allowed")));
    }

    @Test
    void preservesLoginWithoutOrigin() {
        given().contentType("application/json")
                .body(Map.of("username", "student", "password", "Workshop123!"))
                .post("/auth/login").then().statusCode(200).body("user.username", is("student"));
    }
}
