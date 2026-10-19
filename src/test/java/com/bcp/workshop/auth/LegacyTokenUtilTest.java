package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class LegacyTokenUtilTest {
    @Test
    void characterizesUnchangedLegacyFormat() {
        long before = Instant.now().getEpochSecond();
        String token = LegacyTokenUtil.create("student", "STUDENT");
        long after = Instant.now().getEpochSecond();
        String[] fields = new String(Base64.getDecoder().decode(token), StandardCharsets.UTF_8).split("\\|");
        assertEquals(4, fields.length);
        assertEquals("student", fields[0]);
        assertEquals("STUDENT", fields[1]);
        long expiry = Long.parseLong(fields[2]);
        assertTrue(expiry >= before + 3600 && expiry <= after + 3600);
        assertTrue("WORKSHOP_SECRET_2026".equals(fields[3]), "Legacy laboratory secret must match");
    }
}
