package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SqlCorrectionTest {
    @Test
    void rejectsSqlAsUsername() {
        TestDatabase.database().initialize();
        assertNull(TestDatabase.service().authenticate("' OR '1'='1' --", "wrong"));
    }

    @Test
    void rejectsSqlAsPassword() {
        TestDatabase.database().initialize();
        assertNull(TestDatabase.service().authenticate("student", "' OR '1'='1' --"));
    }

    @Test
    void supportsLiteralQuotesWithoutChangingRows() throws Exception {
        TestDatabase.database().initialize();
        TestDatabase.insert("o'brien", "pass'word", "Quoted", "STUDENT");
        var user = TestDatabase.service().authenticate("o'brien", "pass'word");
        assertNotNull(user);
        assertEquals("o'brien", user.username());
        assertEquals("Quoted", user.displayName());
        try (var connection = TestDatabase.connection();
             var statement = connection.prepareStatement("SELECT COUNT(*) FROM legacy_users WHERE username=?")) {
            statement.setString(1, "o'brien");
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
            }
        }
    }
}
