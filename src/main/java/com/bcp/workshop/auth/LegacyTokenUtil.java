package com.bcp.workshop.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

public final class LegacyTokenUtil {
    public static final int EXPIRATION_SECONDS = 3600;
    private static final String SECRET = "WORKSHOP_SECRET_2026";

    private LegacyTokenUtil() {}

    public static String create(String username, String role) {
        long expiry = Instant.now().plusSeconds(EXPIRATION_SECONDS).getEpochSecond();
        String value = username + "|" + role + "|" + expiry + "|" + SECRET;
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
