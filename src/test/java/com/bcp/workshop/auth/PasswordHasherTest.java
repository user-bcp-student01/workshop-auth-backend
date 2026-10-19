package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {
    @Test
    void saltsAndVerifiesPasswordsWithoutTruncation() {
        PasswordHasher hasher = TestDatabase.hasher();
        for (String password : new String[]{"test-password", "á".repeat(80), " ".repeat(4), ""}) {
            String first = hasher.hash(password);
            String second = hasher.hash(password);
            assertNotEquals(first, second);
            assertTrue(hasher.verify(password, first));
            assertFalse(hasher.verify(password + "x", first));
            assertEquals("10000", first.split("\\$")[1]);
        }
    }

    @Test
    void rejectsInvalidConfigurationAndStoredHashesExplicitly() {
        assertThrows(IllegalArgumentException.class, () -> new PasswordHasher(0));
        assertThrows(IllegalArgumentException.class, () -> new PasswordHasher(PasswordHasher.MAX_ITERATIONS + 1));
        PasswordHasher hasher = TestDatabase.hasher();
        for (String hash : new String[]{"plaintext", "bcrypt$10000$x$x", "pbkdf2-sha256$0$x$x",
                "pbkdf2-sha256$no$x$x", "pbkdf2-sha256$10000$?$?",
                "pbkdf2-sha256$10000$YQ$YQ",
                "pbkdf2-sha256$10000$AAAAAAAAAAAAAAAAAAAAAA$YQ"}) {
            assertThrows(IllegalArgumentException.class, () -> hasher.verify("test", hash));
        }
    }

    @Test
    void measuresDefaultCostAndUsesStoredIterations() {
        PasswordHasher defaultHasher = new PasswordHasher(600_000);
        long start = System.nanoTime();
        String hash = defaultHasher.hash("laboratory-benchmark");
        assertTrue(TestDatabase.hasher().verify("laboratory-benchmark", hash));
        long milliseconds = (System.nanoTime() - start) / 1_000_000;
        System.out.println("PBKDF2 600000 iterations hash+verify milliseconds=" + milliseconds);
        assertEquals("600000", hash.split("\\$")[1]);
    }
}
