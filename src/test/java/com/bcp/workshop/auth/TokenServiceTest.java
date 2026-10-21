package com.bcp.workshop.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenServiceTest {
    @Test
    void failsClosedWhenSigningKeyIsMissingOrInvalid() throws Exception {
        TokenService missingKeyService = new TokenService(new ObjectMapper(), Optional.empty(), Clock.systemUTC());
        IllegalStateException missingKey = assertThrows(IllegalStateException.class, missingKeyService::initialize);
        assertEquals("RSA token signing key is required", missingKey.getMessage());
        assertThrows(IllegalStateException.class, () -> missingKeyService.issue("student", "STUDENT", "client"));

        Path invalidKeyPath = Files.createTempFile(Path.of("target"), "issue10-invalid-key-", ".pem");
        try {
            Files.writeString(invalidKeyPath, "not a private key");
            TokenService invalidKeyService = new TokenService(new ObjectMapper(),
                    Optional.of(invalidKeyPath.toString()), Clock.systemUTC());
            IllegalStateException invalidKey = assertThrows(IllegalStateException.class, invalidKeyService::initialize);
            assertEquals("RSA token signing key is invalid", invalidKey.getMessage());
            assertFalse(invalidKey.getMessage().contains(invalidKeyPath.toString()));
            assertThrows(IllegalStateException.class,
                    () -> invalidKeyService.issue("student", "STUDENT", "client"));
        } finally {
            Files.deleteIfExists(invalidKeyPath);
        }
    }

    @Test
    void expiresIssuedTokenAtItsUnixSecondBoundary() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        Path privateKeyPath = Files.createTempFile(Path.of("target"), "issue10-expiry-key-", ".pem");
        try {
            Files.writeString(privateKeyPath, pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
            Instant issuanceInstant = Instant.parse("2026-10-21T12:00:00Z");
            TokenService tokenService = new TokenService(new ObjectMapper(), Optional.of(privateKeyPath.toString()),
                    Clock.fixed(issuanceInstant, ZoneOffset.UTC));
            tokenService.initialize();

            TokenService.IssuedToken issued = tokenService.issue("student", "STUDENT", "angular-workshop");
            String[] segments = issued.value().split("\\.", -1);
            assertEquals(3, segments.length);
            Map<String, Object> claims = new ObjectMapper().readValue(
                    Base64.getUrlDecoder().decode(segments[1]), new TypeReference<>() {});
            long expiration = ((Number) claims.get("exp")).longValue();
            assertEquals(issuanceInstant.getEpochSecond(), ((Number) claims.get("iat")).longValue());
            assertEquals(issued.issuedAt() + 3600, expiration);
            assertEquals(issued.expiresAt(), expiration);
            assertEquals(3600, issued.expiresIn());

            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(keyPair.getPublic());
            verifier.update((segments[0] + "." + segments[1]).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            assertTrue(verifier.verify(Base64.getUrlDecoder().decode(segments[2])));

            assertFalse(isExpired(claims, Clock.fixed(Instant.ofEpochSecond(expiration - 1), ZoneOffset.UTC)));
            assertTrue(isExpired(claims, Clock.fixed(Instant.ofEpochSecond(expiration + 1), ZoneOffset.UTC)));
        } finally {
            Files.deleteIfExists(privateKeyPath);
        }
    }

    private boolean isExpired(Map<String, Object> claims, Clock clock) {
        return clock.instant().getEpochSecond() >= ((Number) claims.get("exp")).longValue();
    }

    private String pem(String type, byte[] encoded) {
        String value = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded);
        return "-----BEGIN " + type + "-----\n" + value + "\n-----END " + type + "-----\n";
    }
}