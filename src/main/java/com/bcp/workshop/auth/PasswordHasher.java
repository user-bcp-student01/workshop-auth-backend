package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@ApplicationScoped
public class PasswordHasher {
    static final int MIN_ITERATIONS = 10_000;
    static final int MAX_ITERATIONS = 2_000_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BYTES = 32;
    private static final int BITS_PER_BYTE = 8;
    private static final String FORMAT = "pbkdf2-sha256";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private final SecureRandom random = new SecureRandom();
    private final int iterations;

    @Inject
    public PasswordHasher(@ConfigProperty(name = "auth.password.iterations") int iterations) {
        validateIterations(iterations);
        this.iterations = iterations;
    }

    public String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] key = derive(password, salt, iterations);
        return FORMAT + "$" + iterations + "$" + encode(salt) + "$" + encode(key);
    }

    public boolean verify(String password, String encodedHash) {
        String[] fields = encodedHash.split("\\$", -1);
        if (fields.length != 4 || !FORMAT.equals(fields[0])) {
            throw new IllegalArgumentException("Unsupported password hash format");
        }
        int storedIterations = Integer.parseInt(fields[1]);
        validateIterations(storedIterations);
        byte[] salt = Base64.getDecoder().decode(fields[2]);
        byte[] expected = Base64.getDecoder().decode(fields[3]);
        if (salt.length != SALT_BYTES || expected.length != KEY_BYTES) {
            throw new IllegalArgumentException("Invalid password hash parameters");
        }
        return MessageDigest.isEqual(expected, derive(password, salt, storedIterations));
    }

    private byte[] derive(String password, byte[] salt, int rounds) {
        char[] characters = password.toCharArray();
        PBEKeySpec specification = new PBEKeySpec(characters, salt, rounds, KEY_BYTES * BITS_PER_BYTE);
        Arrays.fill(characters, '\0');
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(specification).getEncoded();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Password hashing unavailable", error);
        } finally {
            specification.clearPassword();
        }
    }

    private static void validateIterations(int iterations) {
        if (iterations < MIN_ITERATIONS || iterations > MAX_ITERATIONS) {
            throw new IllegalArgumentException("Password iterations outside supported range");
        }
    }

    private static String encode(byte[] bytes) {
        return Base64.getEncoder().withoutPadding().encodeToString(bytes);
    }
}
