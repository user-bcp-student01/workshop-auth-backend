package com.bcp.workshop.auth;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

public class SigningKeyResource implements QuarkusTestResourceLifecycleManager {
    private Path privateKey;

    @Override
    public Map<String, String> start() {
        try {
            privateKey = writeKey(generate(2048));
            return Map.of("auth.token.private-key", privateKey.toAbsolutePath().toString());
        } catch (Exception error) {
            throw new IllegalStateException("Unable to create isolated signing key", error);
        }
    }

    @Override
    public void stop() {
        try {
            Files.deleteIfExists(privateKey);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Unable to remove isolated signing key", error);
        }
    }

    static KeyPair generate(int bits) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(bits);
        return generator.generateKeyPair();
    }

    static Path writeKey(KeyPair pair) throws Exception {
        Path file = TestDatabase.directory().resolve("test-signing.pem");
        Files.writeString(file, "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n");
        file.toFile().deleteOnExit();
        return file;
    }
}
