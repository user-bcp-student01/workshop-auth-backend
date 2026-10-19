package com.bcp.workshop.auth;

import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.jwt.NumericDate;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class TokenServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-19T20:00:00Z");
    private static final UserRecord USER = new UserRecord(1, "student", "Student", "STUDENT");

    @Test
    void signsVerifiableClaimsAndRejectsExpiredTamperedAndWrongKey() throws Exception {
        KeyPair pair = SigningKeyResource.generate(2048);
        var service = new TokenService(SigningKeyResource.writeKey(pair).toString(), "workshop-auth-backend",
                "workshop-api", Clock.fixed(NOW, ZoneOffset.UTC));
        String token = service.create(USER);
        var verifier = verifier(pair, NOW);
        var claims = verifier.processToClaims(token);
        assertEquals("student", claims.getSubject());
        assertEquals("STUDENT", claims.getStringClaimValue("role"));
        assertEquals(NOW.getEpochSecond(), claims.getIssuedAt().getValue());
        assertEquals(3600, claims.getExpirationTime().getValue() - claims.getIssuedAt().getValue());
        String[] segments = token.split("\\.");
        String tampered = segments[0] + "." + segments[1] + "." +
                (segments[2].startsWith("A") ? "B" : "A") + segments[2].substring(1);
        assertThrows(InvalidJwtException.class, () -> verifier.processToClaims(tampered));
        assertThrows(InvalidJwtException.class, () -> verifier(SigningKeyResource.generate(2048), NOW).processToClaims(token));
        assertThrows(InvalidJwtException.class, () -> verifier(pair, NOW.plusSeconds(3601)).processToClaims(token));
    }

    @Test
    void failsExplicitlyForMissingInvalidWeakKeysAndBlankClaims() throws Exception {
        String path = SigningKeyResource.writeKey(SigningKeyResource.generate(2048)).toString();
        for (String[] config : new String[][]{{"", "issuer", "audience"}, {path, "", "audience"},
                {path, "issuer", ""}}) {
            assertThrows(IllegalArgumentException.class, () -> new TokenService(config[0], config[1], config[2]));
        }
        assertThrows(IllegalStateException.class,
                () -> new TokenService(TestDatabase.directory().resolve("missing.pem").toString(), "issuer", "audience"));
        var invalid = TestDatabase.directory().resolve("invalid.pem");
        Files.writeString(invalid, "not a private key");
        assertThrows(IllegalStateException.class, () -> new TokenService(invalid.toString(), "issuer", "audience"));
        var malformed = TestDatabase.directory().resolve("malformed.pem");
        Files.writeString(malformed, "AAAA");
        assertThrows(IllegalStateException.class, () -> new TokenService(malformed.toString(), "issuer", "audience"));
        String weak = SigningKeyResource.writeKey(SigningKeyResource.generate(1024)).toString();
        assertThrows(IllegalStateException.class, () -> new TokenService(weak, "issuer", "audience"));
    }

    private org.jose4j.jwt.consumer.JwtConsumer verifier(KeyPair pair, Instant now) {
        return new JwtConsumerBuilder().setRequireExpirationTime().setRequireIssuedAt()
                .setExpectedIssuer("workshop-auth-backend").setExpectedAudience("workshop-api")
                .setEvaluationTime(NumericDate.fromSeconds(now.getEpochSecond()))
                .setVerificationKey(pair.getPublic())
                .setJwsAlgorithmConstraints(org.jose4j.jwa.AlgorithmConstraints.ConstraintType.PERMIT,
                        AlgorithmIdentifiers.RSA_USING_SHA256).build();
    }
}
