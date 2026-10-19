package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PasswordMigrationTest {
    static Connection legacy(Path file) throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE legacy_users(id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT UNIQUE,password TEXT NOT NULL,display_name TEXT NOT NULL,role TEXT NOT NULL)
                    """);
        }
        return connection;
    }

    static void insert(Connection connection, int id, String username, String password) throws Exception {
        try (var statement = connection.prepareStatement(
                "INSERT INTO legacy_users VALUES (?,?,?,?,?)")) {
            statement.setInt(1, id);
            statement.setString(2, username);
            statement.setString(3, password);
            statement.setString(4, "Display " + id);
            statement.setString(5, "TRAINER");
            statement.executeUpdate();
        }
    }

    @Test
    void migratesAllUsersPreservingIdentityAndDoesNotRehash() throws Exception {
        Path file = TestDatabase.directory().resolve("legacy.db");
        String longPassword = "ü".repeat(100);
        try (var connection = legacy(file)) {
            insert(connection, 7, "student", longPassword);
            insert(connection, 9, "trainer", "shared");
            insert(connection, 11, "another", "shared");
            PasswordMigration migration = new PasswordMigration(TestDatabase.hasher());
            migration.migrate(connection);
            List<String> before = hashes(connection);
            migration.migrate(connection);
            assertEquals(before, hashes(connection));
            assertEquals(3, before.size());
            assertNotEquals(before.get(1), before.get(2));
            assertTrue(TestDatabase.hasher().verify(longPassword, before.getFirst()));
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT id,username,display_name,role FROM legacy_users ORDER BY id")) {
                assertTrue(rows.next());
                assertEquals(7, rows.getInt("id"));
                assertEquals("student", rows.getString("username"));
                assertEquals("Display 7", rows.getString("display_name"));
                assertEquals("TRAINER", rows.getString("role"));
            }
        }
        LegacyDatabase database = TestDatabase.database("jdbc:sqlite:" + file);
        database.initialize();
        AuthService service = new AuthService(database, TestDatabase.hasher(), TestDatabase.tokens());
        assertNotNull(service.authenticate("student", longPassword));
        assertNull(service.authenticate("student", "Workshop123!"));
        assertNotNull(service.authenticate("trainer", "shared"));
    }

    @Test
    void rollsBackAllChangesWhenHashingFailsMidway() throws Exception {
        Path file = TestDatabase.directory().resolve("rollback.db");
        try (var connection = legacy(file)) {
            insert(connection, 1, "first", "first-password");
            insert(connection, 2, "second", "second-password");
            PasswordHasher failingHasher = new PasswordHasher(10_000) {
                private int calls;

                @Override
                public String hash(String password) {
                    if (++calls == 2) {
                        throw new IllegalStateException("injected hashing failure");
                    }
                    return super.hash(password);
                }
            };
            assertThrows(IllegalStateException.class, () -> new PasswordMigration(failingHasher).migrate(connection));
            assertTrue(connection.getAutoCommit());
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT COUNT(*), MIN(password) FROM legacy_users")) {
                assertTrue(rows.next());
                assertEquals(2, rows.getInt(1));
                assertEquals("first-password", rows.getString(2));
            }
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE name='hashed_users'")) {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt(1));
            }
            new PasswordMigration(TestDatabase.hasher()).migrate(connection);
            assertEquals(2, hashes(connection).size());
        }
    }

    @Test
    void rejectsUnknownSchemaAndDoesNotCommitPartialMigration() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + TestDatabase.directory().resolve("unknown.db"));
             var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE legacy_users(unexpected TEXT)");
            assertThrows(java.sql.SQLException.class, () -> new PasswordMigration(TestDatabase.hasher()).migrate(connection));
            assertTrue(connection.getAutoCommit());
        }
    }

    private List<String> hashes(Connection connection) throws Exception {
        List<String> hashes = new ArrayList<>();
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT password_hash FROM legacy_users ORDER BY id")) {
            while (rows.next()) {
                hashes.add(rows.getString(1));
            }
        }
        return hashes;
    }

    @Test
    void neverEnablesAutocommitAfterFailedRollback() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + TestDatabase.directory().resolve("cleanup.db"));
             var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE legacy_users(unexpected TEXT)");
            Connection failingRollback = Connection.class.cast(java.lang.reflect.Proxy.newProxyInstance(
                    Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, arguments) -> {
                        if (method.getName().equals("rollback")) {
                            throw new java.sql.SQLException("injected rollback failure");
                        }
                        try {
                            return method.invoke(connection, arguments);
                        } catch (java.lang.reflect.InvocationTargetException error) {
                            throw error.getCause();
                        }
                    }));
            var failure = assertThrows(java.sql.SQLException.class,
                    () -> new PasswordMigration(TestDatabase.hasher()).migrate(failingRollback));
            assertEquals(1, failure.getSuppressed().length);
            assertFalse(connection.getAutoCommit());
            connection.rollback();
        }
    }
}
