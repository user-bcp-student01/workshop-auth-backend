package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class HealthResourceTest {
    private static final String HEALTH = "/q/health";

    @Test
    void reportsUpWithoutAuthenticationAndWithNoChecks() {
        assertUp(given().accept("application/json").get(HEALTH));
    }

    @Test
    void acceptsXmlPreferenceButStillReturnsJson() {
        assertUp(given().accept("application/xml").get(HEALTH));
    }

    @Test
    void characterizesDocumentedPostBehaviorWithoutInventing405() {
        assertUp(given().contentType("application/json").body("{}").post(HEALTH));
    }

    @Test
    void characterizesKnownHealthCoverageGapDespiteLoginFailure() throws Exception {
        TestDatabase.insert("health-invalid-hash", "test-only", "Health fixture", "STUDENT");
        try {
            try (var connection = TestDatabase.connection();
                 var statement = connection.prepareStatement(
                         "UPDATE legacy_users SET password_hash=? WHERE username=?")) {
                statement.setString(1, "invalid-test-hash");
                statement.setString(2, "health-invalid-hash");
                statement.executeUpdate();
            }
            given().contentType("application/json")
                    .body(Map.of("username", "health-invalid-hash", "password", "test-only"))
                    .post("/auth/login").then().statusCode(500)
                    .body("message", is("Internal server error"));
            assertUp(given().get(HEALTH));
        } finally {
            try (var connection = TestDatabase.connection();
                 var statement = connection.prepareStatement("DELETE FROM legacy_users WHERE username=?")) {
                statement.setString(1, "health-invalid-hash");
                statement.executeUpdate();
            }
        }
    }

    @Test
    void unavailableServiceIsTransportFailureNotHttpError() throws Exception {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        // Reserve a port without a listener: no unrelated process can receive the request.
        try (Socket reserved = new Socket();
             HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            reserved.bind(new InetSocketAddress(loopback, 0));
            URI endpoint = URI.create("http://127.0.0.1:" + reserved.getLocalPort() + HEALTH);
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(2)).GET().build();
            assertThrows(java.net.ConnectException.class,
                    () -> client.send(request, HttpResponse.BodyHandlers.discarding()));
        }
    }

    private void assertUp(io.restassured.response.Response response) {
        response.then().statusCode(200).contentType("application/json")
                .body("$", aMapWithSize(2)).body("status", is("UP")).body("checks", empty());
    }
}
