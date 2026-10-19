package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CorsValidationTest {
    @Test
    void acceptsExplicitOriginLists() {
        assertDoesNotThrow(() -> new CorsConfiguration("http://localhost:4200, https://allowed.example"));
    }

    @Test
    void rejectsEmptyWildcardRegexAndNonOriginValues() {
        for (String value : new String[]{"", " ", "*", "/.*/", "null", "file:///tmp",
                "https://example.com/path", "https://user@example.com",
                "https://example.com?q=x", "https://example.com#fragment",
                "http://localhost:4200,", "https://["}) {
            assertThrows(IllegalArgumentException.class, () -> new CorsConfiguration(value), value);
        }
    }
}
