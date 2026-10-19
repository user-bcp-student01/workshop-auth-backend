package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class PasswordStorageTest {
    @Test
    void storesOnlySaltedHashesAndPreservesExistingIdentities() throws Exception {
        LegacyDatabase database = TestDatabase.database();
        database.initialize();
        var columns = new HashSet<String>();
        try (var connection = TestDatabase.connection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("PRAGMA table_info(legacy_users)")) {
            while (rows.next()) {
                columns.add(rows.getString("name"));
            }
        }
        assertTrue(columns.contains("password_hash"), "Password hash column is required");
        assertFalse(columns.contains("password"), "Plaintext password column must be removed");
        try (var connection = TestDatabase.connection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT password_hash FROM legacy_users")) {
            while (rows.next()) {
                assertTrue(rows.getString(1).startsWith("pbkdf2-sha256$"));
            }
        }
    }
}
