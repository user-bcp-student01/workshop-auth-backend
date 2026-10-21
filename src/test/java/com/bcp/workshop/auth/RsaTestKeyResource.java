package com.bcp.workshop.auth;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

public class RsaTestKeyResource implements QuarkusTestResourceLifecycleManager {
    private static final Path KEY_DIRECTORY = Path.of("target", "test-keys").toAbsolutePath();
    private static final Path PRIVATE_KEY_PATH = KEY_DIRECTORY.resolve("auth-test-private.pem");
    private static final Path PUBLIC_KEY_PATH = KEY_DIRECTORY.resolve("auth-test-public.pem");

    @Override
    public Map<String, String> start() {
        try {
            Files.createDirectories(KEY_DIRECTORY);
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            Files.writeString(PRIVATE_KEY_PATH, pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
            Files.writeString(PUBLIC_KEY_PATH, pem("PUBLIC KEY", keyPair.getPublic().getEncoded()));
            return Map.of("auth.token.private-key", PRIVATE_KEY_PATH.toString());
        } catch (GeneralSecurityException | IOException error) {
            throw new IllegalStateException("Unable to create test signing key", error);
        }
    }

    @Override
    public void stop() {
        try {
            Files.deleteIfExists(PRIVATE_KEY_PATH);
            Files.deleteIfExists(PUBLIC_KEY_PATH);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to remove test signing key files");
        }
    }

    static Path publicKeyPath() {
        return PUBLIC_KEY_PATH;
    }

    private String pem(String type, byte[] encoded) {
        String value = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded);
        return "-----BEGIN " + type + "-----\n" + value + "\n-----END " + type + "-----\n";
    }
}