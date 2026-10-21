package com.bcp.workshop.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@QuarkusTest
@QuarkusTestResource(RsaTestKeyResource.class)
class AuthResourceTest {
    @Test
    void logsInWithWorkshopUser() throws Exception {
        var response = given()
                .contentType("application/json")
            .body(loginRequestWithClient("student", "Workshop123!"))
                .when().post("/auth/login").then()
                .statusCode(200)
                .contentType("application/json")
                .body("user.username", is("student"))
                .body("token", notNullValue())
                .extract().response();

        Map<String, Object> body = response.jsonPath().getMap("");
        assertEquals(Map.of("username", "student", "displayName", "Workshop Student", "role", "STUDENT"),
                body.get("user"));
        assertEquals("authenticated-with-legacy-flow", body.get("debug"));
        assertIssuedJwt(body, "angular-workshop");
    }

    @Test
    void preservesClientWhitespaceInIssuedJwtClaim() throws Exception {
        String client = " angular-workshop ";
        Map<String, Object> request = loginRequest("student", "Workshop123!");
        request.put("client", client);
        var response = given().contentType("application/json").body(request)
                .post("/auth/login").then().statusCode(200).extract().response();

        assertIssuedJwt(response.jsonPath().getMap(""), client);
    }

    @Test
    void requiresClientAndPreservesExistingValidationPrecedence() {
        assertError(loginRequest(null, null), 400, "Username is required");
        assertError(loginRequest("student", null), 400, "Password is required");

        List<Map<String, Object>> invalidClients = new ArrayList<>();
        invalidClients.add(loginRequest("student", "Workshop123!"));
        for (Object client : new Object[]{null, "", "   "}) {
            Map<String, Object> request = loginRequest("student", "Workshop123!");
            request.put("client", client);
            invalidClients.add(request);
        }

        for (Map<String, Object> request : invalidClients) {
            var response = given().contentType("application/json").body(request)
                    .post("/auth/login").then().extract().response();
            assertEquals(400, response.getStatusCode());
            assertTrue(response.getContentType().startsWith("application/json"));
            Map<String, Object> error = response.jsonPath().getMap("");
            assertTrue(error.containsKey("message"));
            assertFalse(error.containsKey("token"));
        }
    }

    @Test
    void rejectsClientWithIncompatibleJsonTypeWithoutIssuingToken() {
        Map<String, Object> request = loginRequest("student", "Workshop123!");
        request.put("client", Map.of("name", "angular-workshop"));

        var response = given().contentType("application/json").body(request)
                .post("/auth/login").then().extract().response();
        assertEquals(400, response.getStatusCode());
        assertFalse(response.asString().contains("\"token\""));
    }

    @Test
    void characterizesCurrentValidationPrecedenceAndCredentialFailures() {
        given().contentType("application/json").body("null").post("/auth/login")
                .then().statusCode(400).body("message", is("Request is null"));
        given().contentType("application/json").post("/auth/login")
                .then().statusCode(400).body("message", is("Request is null"));

        assertError(loginRequest(null, null), 400, "Username is required");
        assertError(loginRequest("", null), 400, "Username is required");
        assertError(loginRequest("student", null), 400, "Password is required");
        assertError(loginRequest("student", ""), 400, "Password is required");
        assertError(loginRequestWithClient("missing-user", "Workshop123!"), 401,
                "Invalid user missing-user or password");
        assertError(loginRequestWithClient("student", "wrong-password"), 401,
                "Invalid user student or password");
    }

    @Test
    void returnsGenericInternalErrorForSqlFailureWithoutSensitiveLogDetails() throws Exception {
        String databaseUrl = System.getenv("AUTH_DB_URL");
        assumeTrue(databaseUrl != null && databaseUrl.startsWith("jdbc:sqlite:./target/issue10-characterization-"),
                "This schema-changing characterization requires an isolated SQLite file under target");

        given().contentType("application/json").body(loginRequestWithClient("student", "Workshop123!"))
            .post("/auth/login").then().statusCode(200);

        try (Connection connection = DriverManager.getConnection(databaseUrl);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE legacy_users RENAME TO legacy_users_characterization_backup");
        }

        StringBuilder capturedLog = new StringBuilder();
        Logger logger = Logger.getLogger(AuthResource.class.getName());
        Handler capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                capturedLog.append(record.getMessage()).append('\n');
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(capture);
        try {
            assertError(loginRequestWithClient("student", "Workshop123!"), 500,
                    "Internal server error");
        } finally {
            logger.removeHandler(capture);
            try (Connection connection = DriverManager.getConnection(databaseUrl);
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE legacy_users_characterization_backup RENAME TO legacy_users");
            }
        }

        String logOutput = capturedLog.toString();
        assertTrue(logOutput.contains("Authentication request failed"));
        assertFalse(logOutput.contains("legacy_users"));
        assertFalse(logOutput.toLowerCase().contains("exception"));
        assertFalse(logOutput.contains("Workshop123!"));
        assertFalse(logOutput.contains("WORKSHOP_SECRET_2026"));
        assertFalse(logOutput.contains("-----BEGIN PRIVATE KEY-----"));
    }

    @Test
    void returnsGenericInternalErrorEntityWithoutExceptionDetails() {
        AuthResource resource = new AuthResource();
        resource.database = new LegacyDatabase() {
            @Override
            public UserRecord findByCredentials(String username, String password) {
                throw new IllegalStateException("characterization failure");
            }
        };
        LoginRequest request = new LoginRequest();
        request.username = "student";
        request.password = "Workshop123!";
        request.client = "angular-workshop";

        try (Response response = resource.login(request)) {
            assertEquals(500, response.getStatus());
            assertEquals(Map.of("message", "Internal server error"), response.getEntity());
        }
    }

    @Test
    void doesNotLogTokensOrCredentialsForSuccessAndInvalidCredentials() {
        StringBuilder capturedLog = new StringBuilder();
        Logger logger = Logger.getLogger(AuthResource.class.getName());
        Handler capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                capturedLog.append(record.getMessage()).append('\n');
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(capture);
        try {
            var success = given().contentType("application/json")
                    .body(loginRequestWithClient("student", "Workshop123!"))
                    .post("/auth/login").then().statusCode(200).extract().response();
            assertError(loginRequestWithClient("student", "wrong-password"), 401,
                    "Invalid user student or password");
            assertFalse(capturedLog.toString().contains(success.jsonPath().getString("token")));
        } finally {
            logger.removeHandler(capture);
        }
        assertFalse(capturedLog.toString().contains("Workshop123!"));
        assertFalse(capturedLog.toString().contains("wrong-password"));
        assertFalse(capturedLog.toString().contains("WORKSHOP_SECRET_2026"));
    }

    private void assertIssuedJwt(Map<String, Object> body, String expectedClient) throws Exception {
        assertEquals(Set.of("token", "expiresIn", "user", "debug"), body.keySet());
        String token = (String) body.get("token");
        String[] segments = token.split("\\.", -1);
        assertEquals(3, segments.length);
        for (String segment : segments) {
            assertTrue(segment.matches("[A-Za-z0-9_-]+"));
        }

        Map<String, Object> header = decodeJwtSegment(segments[0]);
        Map<String, Object> claims = decodeJwtSegment(segments[1]);
        assertEquals("RS256", header.get("alg"));

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(readTestPublicKey());
        verifier.update((segments[0] + "." + segments[1]).getBytes(StandardCharsets.US_ASCII));
        assertTrue(verifier.verify(Base64.getUrlDecoder().decode(segments[2])));

        assertEquals("student", claims.get("sub"));
        assertEquals(expectedClient, claims.get("client"));
        assertEquals("STUDENT", claims.get("role"));
        assertEquals("workshop-auth-backend", claims.get("iss"));
        Object audience = claims.get("aud");
        assertTrue("workshop-api".equals(audience)
                || audience instanceof List<?> values && values.contains("workshop-api"));

        long issuedAt = ((Number) claims.get("iat")).longValue();
        long expiresAt = ((Number) claims.get("exp")).longValue();
        long expiresIn = ((Number) body.get("expiresIn")).longValue();
        assertEquals(issuedAt + 3600, expiresAt);
        assertTrue(expiresIn > 0);
        assertTrue(Math.abs(expiresIn - (expiresAt - issuedAt)) <= 1);

        String decodedPayload = new String(Base64.getUrlDecoder().decode(segments[1]), StandardCharsets.UTF_8);
        assertFalse(decodedPayload.contains("WORKSHOP_SECRET_2026"));
        assertFalse(decodedPayload.contains("PRIVATE KEY"));
        assertFalse(token.contains("-----BEGIN"));
    }

    private Map<String, Object> decodeJwtSegment(String segment) throws Exception {
        return new ObjectMapper().readValue(Base64.getUrlDecoder().decode(segment),
                new TypeReference<>() {});
    }

    private PublicKey readTestPublicKey() throws Exception {
        String pem = Files.readString(RsaTestKeyResource.publicKeyPath());
        String encoded = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(encoded);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
    }

    private Map<String, Object> loginRequest(String username, String password) {
        Map<String, Object> request = new HashMap<>();
        request.put("username", username);
        request.put("password", password);
        return request;
    }

    private Map<String, Object> loginRequestWithClient(String username, String password) {
        Map<String, Object> request = loginRequest(username, password);
        request.put("client", "angular-workshop");
        return request;
    }

    private void assertError(Map<String, Object> request, int status, String message) {
        var response = given().contentType("application/json").body(request)
                .post("/auth/login").then().statusCode(status)
                .contentType("application/json").extract().jsonPath();
        assertEquals(Map.of("message", message), response.getMap(""));
    }
}
