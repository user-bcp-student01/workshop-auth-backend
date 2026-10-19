package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class EndpointContractTest {
    private static final String LOGIN = "/auth/login";

    @Test
    void rejectsNullAndAbsentHttpBodies() {
        given().contentType("application/json").body("null").post(LOGIN)
                .then().statusCode(400).body("$", aMapWithSize(1))
                .body("message", is("Request is null"));
        given().contentType("application/json").post(LOGIN)
                .then().statusCode(400).body("$", aMapWithSize(1))
                .body("message", is("Request is null"));
    }

    @Test
    void rejectsMissingAndExplicitNullFields() {
        for (String payload : new String[]{"{}", "{\"username\":null,\"password\":\"x\"}"}) {
            given().contentType("application/json").body(payload).post(LOGIN)
                    .then().statusCode(400).body("message", is("Username is required"));
        }
        for (String payload : new String[]{"{\"username\":\"student\"}",
                "{\"username\":\"student\",\"password\":null}"}) {
            given().contentType("application/json").body(payload).post(LOGIN)
                    .then().statusCode(400).body("message", is("Password is required"));
        }
    }

    @Test
    void acceptsNullClientAndDoesNotRequireAuthorizationOrAccept() {
        given().contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":null}")
                .post(LOGIN).then().statusCode(200).contentType("application/json")
                .body("$", aMapWithSize(4)).body("user.username", is("student"));
    }

    @Test
    void rejectsMalformedJsonAndArrayRootWithEmptyBodies() {
        for (String payload : new String[]{"{", "[]"}) {
            given().contentType("application/json").accept("application/json").body(payload)
                    .post(LOGIN).then().statusCode(400).body(is(""));
        }
    }

    @Test
    void rejectsIncompatibleUsernameType() {
        given().contentType("application/json").accept("application/json")
                .body("{\"username\":{},\"password\":\"x\"}").post(LOGIN)
                .then().statusCode(400).contentType("application/json")
                .body("$", aMapWithSize(5))
                .body("objectName", is("LoginRequest"))
                .body("attributeName", is("username"))
                .body("line", is(1)).body("column", is(13)).body("value", is((Object) null));
    }

    @Test
    void rejectsWrongMethodWithEmptyBody() {
        given().get(LOGIN).then().statusCode(405).body(is(""));
    }

    @Test
    void rejectsUnacceptableResponseTypeWithEmptyBody() {
        given().contentType("application/json").accept("application/xml").body("{}")
                .post(LOGIN).then().statusCode(406).body(is(""));
    }

    @Test
    void rejectsUnsupportedRequestTypeWithEmptyBody() {
        given().contentType("text/plain").accept("application/json").body("hello")
                .post(LOGIN).then().statusCode(415).body(is(""));
    }

    @Test
    void characterizesKnownSqlFailureBugAsHttp401() throws Exception {
        try (var connection = TestDatabase.connection(); var statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE legacy_users RENAME TO endpoint_unavailable_users");
            try {
                given().contentType("application/json")
                        .body(Map.of("username", "student", "password", "Workshop123!"))
                        .post(LOGIN).then().statusCode(401).body("$", aMapWithSize(1))
                        .body("message", is("Invalid user student or password"));
            } finally {
                statement.executeUpdate("ALTER TABLE endpoint_unavailable_users RENAME TO legacy_users");
            }
        }
    }

    @Test
    void characterizesKnownInternalErrorDisclosureBugOverHttp() throws Exception {
        TestDatabase.insert("invalid-hash-user", "test-only", "Invalid hash", "STUDENT");
        try {
            try (var connection = TestDatabase.connection();
                 var statement = connection.prepareStatement(
                         "UPDATE legacy_users SET password_hash=? WHERE username=?")) {
                statement.setString(1, "invalid-test-hash");
                statement.setString(2, "invalid-hash-user");
                assertEquals(1, statement.executeUpdate());
            }
            given().contentType("application/json")
                    .body(Map.of("username", "invalid-hash-user", "password", "test-only"))
                    .post(LOGIN).then().statusCode(500).body("$", aMapWithSize(2))
                    .body("message", is("Unsupported password hash format"))
                    .body("exception", is("java.lang.IllegalArgumentException"));
        } finally {
            deleteFixture("invalid-hash-user");
        }
    }

    private void deleteFixture(String username) throws SQLException {
        try (var connection = TestDatabase.connection();
             var statement = connection.prepareStatement("DELETE FROM legacy_users WHERE username=?")) {
            statement.setString(1, username);
            statement.executeUpdate();
        }
    }
}
