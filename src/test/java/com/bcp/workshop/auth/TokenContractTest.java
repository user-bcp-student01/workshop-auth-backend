package com.bcp.workshop.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class TokenContractTest {
    @Test
    void emitsRs256ClaimsInsteadOfReversibleSecret() throws Exception {
        String token = given().contentType("application/json")
                .body(Map.of("username", "student", "password", "Workshop123!"))
                .post("/auth/login").then().statusCode(200).extract().path("token");
        String[] segments = token.split("\\.");
        assertEquals(3, segments.length, "Signed JWT must contain three segments");
        ObjectMapper mapper = new ObjectMapper();
        var header = mapper.readTree(Base64.getUrlDecoder().decode(segments[0]));
        var claims = mapper.readTree(Base64.getUrlDecoder().decode(segments[1]));
        assertEquals("RS256", header.get("alg").asText());
        assertEquals("student", claims.get("sub").asText());
        assertEquals("STUDENT", claims.get("role").asText());
        assertEquals("workshop-auth-backend", claims.get("iss").asText());
        assertTrue(claims.get("aud").toString().contains("workshop-api"));
        assertEquals(3600, claims.get("exp").asLong() - claims.get("iat").asLong());
        assertFalse(new String(Base64.getUrlDecoder().decode(segments[1]), StandardCharsets.UTF_8)
                .contains("SECRET"));
    }
}
