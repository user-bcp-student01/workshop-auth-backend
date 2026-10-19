package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@io.quarkus.test.common.QuarkusTestResource(SigningKeyResource.class)
class AuthResourceTest {
    @BeforeAll
    static void fixtures() throws Exception {
        TestDatabase.database().initialize();
        TestDatabase.insert("html-user", " secret ", "<b>A & B</b>", "STUDENT");
        TestDatabase.insert("long-user", "á".repeat(80), "Unicode", "TRAINER");
    }

    @Test
    void logsInWithWorkshopUser() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("student"))
                .body("token", notNullValue());
    }

    @Test
    void preservesCompleteSuccessAndOptionalClient() {
        for (String username : new String[]{"student", "trainer"}) {
            String password = username.equals("student") ? "Workshop123!" : "Trainer123!";
            String displayName = username.equals("student") ? "Workshop Student" : "Workshop Trainer";
            given().contentType("application/json")
                    .body(Map.of("username", username, "password", password, "unknown", true))
                    .post("/auth/login").then().statusCode(200)
                    .body("$", aMapWithSize(4))
                    .body("user", aMapWithSize(3))
                    .body("user.username", is(username))
                    .body("user.displayName", is(displayName))
                    .body("user.role", is(username.toUpperCase()))
                    .body("expiresIn", is(3600))
                    .body("debug", is("authenticated-with-legacy-flow"));
        }
    }

    @Test
    void preservesValidationOrderAndMessages() {
        for (String blank : new String[]{"", " ", "\t"}) {
            given().contentType("application/json").body(Map.of("username", blank, "password", blank))
                    .post("/auth/login").then().statusCode(400).body("message", is("Username is required"));
            given().contentType("application/json").body(Map.of("username", "student", "password", blank))
                    .post("/auth/login").then().statusCode(400).body("message", is("Password is required"));
        }
        given().contentType("application/json").body("{}").post("/auth/login")
                .then().statusCode(400).body("message", is("Username is required"));
        given().contentType("application/json").body("{\"username\":\"student\",\"password\":null}")
                .post("/auth/login").then().statusCode(400).body("message", is("Password is required"));
        assertEquals(Map.of("message", "Request is null"), new AuthResource().login(null).getEntity());
    }

    @Test
    void preservesInvalidCredentialsAndWhitespace() {
        for (String username : new String[]{"missing", " student ", "student"}) {
            given().contentType("application/json").body(Map.of("username", username, "password", "wrong"))
                    .post("/auth/login").then().statusCode(401)
                    .body("message", is("Invalid user " + username + " or password"));
        }
        given().contentType("application/json").body(Map.of("username", "student", "password", " Workshop123! "))
                .post("/auth/login").then().statusCode(401);
        given().contentType("application/json").body(Map.of("username", " student ", "password", "Workshop123!"))
                .post("/auth/login").then().statusCode(401);
    }

    @Test
    void preservesEscapingLongPasswordsAndExactWhitespace() {
        given().contentType("application/json").body(Map.of("username", "html-user", "password", " secret "))
                .post("/auth/login").then().statusCode(200)
                .body("user.displayName", is("&lt;b&gt;A &amp; B&lt;/b&gt;"));
        given().contentType("application/json").body(Map.of("username", "long-user", "password", "á".repeat(80)))
                .post("/auth/login").then().statusCode(200);
        given().contentType("application/json").body(Map.of("username", "long-user", "password", "á".repeat(79) + "b"))
                .post("/auth/login").then().statusCode(401);
    }

    @Test
    void preservesExactHtml4EscapingAcrossDependencyUpdates() throws Exception {
        TestDatabase.insert("escape-contract", "test-only", "&<>\"' Ω中文 &amp;", "STUDENT");
        given().contentType("application/json")
                .body(Map.of("username", "escape-contract", "password", "test-only"))
                .post("/auth/login").then().statusCode(200)
                .body("$", aMapWithSize(4)).body("user", aMapWithSize(3))
                .body("user.username", is("escape-contract"))
                .body("user.displayName", is("&amp;&lt;&gt;&quot;' &Omega;中文 &amp;amp;"))
                .body("user.role", is("STUDENT"))
                .body("expiresIn", is(3600)).body("token", notNullValue())
                .body("debug", is("authenticated-with-legacy-flow"));
    }

    @Test
    void hidesExceptionDetailsAndLogsCauseOnceWithoutRequestSecrets() {
        for (String message : new String[]{"test failure", null}) {
            var records = new java.util.ArrayList<java.util.logging.LogRecord>();
            var logger = java.util.logging.Logger.getLogger(AuthResource.class.getName());
            var handler = new java.util.logging.Handler() {
                @Override
                public void publish(java.util.logging.LogRecord record) {
                    records.add(record);
                }
                @Override
                public void flush() {}
                @Override
                public void close() {}
            };
            AuthResource resource = new AuthResource();
            resource.service = new AuthService(TestDatabase.database(), TestDatabase.hasher(), TestDatabase.tokens()) {
                @Override
                public UserRecord authenticate(String username, String password) {
                    throw new IllegalStateException(message);
                }
            };
            LoginRequest request = new LoginRequest();
            request.username = "student";
            request.password = "request-password-never-log";
            logger.addHandler(handler);
            try {
                var response = resource.login(request);
                assertEquals(500, response.getStatus());
                assertEquals(Map.of("message", "Internal server error"), response.getEntity());
                assertEquals(1, records.size());
                assertEquals(message, records.getFirst().getThrown().getMessage());
                var trace = new java.io.StringWriter();
                records.getFirst().getThrown().printStackTrace(new java.io.PrintWriter(trace));
                org.junit.jupiter.api.Assertions.assertFalse(
                        (records.getFirst().getMessage() + trace).contains(request.password));
            } finally {
                logger.removeHandler(handler);
            }
        }
    }
}
