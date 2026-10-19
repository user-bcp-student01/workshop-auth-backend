package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LegacyDatabaseTest {
    @Test
    void characterizesLookupAndIdempotentSeed() {
        LegacyDatabase database = TestDatabase.database();
        database.initialize();
        AuthService service = new AuthService(database, TestDatabase.hasher(), TestDatabase.tokens());
        UserRecord before = service.authenticate("student", "Workshop123!");
        assertNotNull(before);
        assertEquals("Workshop Student", before.displayName());
        assertEquals("STUDENT", before.role());
        database.initialize();
        assertEquals(before, service.authenticate("student", "Workshop123!"));
        assertNull(service.authenticate("student", "wrong"));
        assertNull(service.authenticate("absent", "wrong"));
    }

    @Test
    void propagatesQueryFailureWithCauseAndGenericHttp500() throws Exception {
        LegacyDatabase database = TestDatabase.database();
        database.initialize();
        try (var connection = TestDatabase.connection(); var statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE legacy_users RENAME TO unavailable_users");
            try {
                var failure = assertThrows(AuthenticationPersistenceException.class,
                        () -> database.findByUsername("student"));
                assertInstanceOf(java.sql.SQLException.class, failure.getCause());
                AuthResource resource = new AuthResource();
                resource.service = new AuthService(database, TestDatabase.hasher(), TestDatabase.tokens());
                LoginRequest request = new LoginRequest();
                request.username = "student";
                request.password = "Workshop123!";
                var response = resource.login(request);
                assertEquals(500, response.getStatus());
                assertEquals(java.util.Map.of("message", "Internal server error"), response.getEntity());
            } finally {
                statement.executeUpdate("ALTER TABLE unavailable_users RENAME TO legacy_users");
            }
        }
    }

    @Test
    void initializesFreshDatabaseAndPreservesCustomCredentials() throws Exception {
        String url = "jdbc:sqlite:" + TestDatabase.directory().resolve("fresh.db");
        PasswordHasher hasher = TestDatabase.hasher();
        var database = new LegacyDatabase(url, hasher, new PasswordMigration(hasher),
                Optional.of("custom-student"), Optional.of("custom-trainer"));
        database.initialize();
        var service = new AuthService(database, hasher, TestDatabase.tokens());
        assertNotNull(service.authenticate("student", "custom-student"));
        assertNotNull(service.authenticate("trainer", "custom-trainer"));
        var withoutSeeds = new LegacyDatabase(url, hasher, new PasswordMigration(hasher),
                Optional.empty(), Optional.empty());
        withoutSeeds.initialize();
        assertNotNull(service.authenticate("student", "custom-student"));
        assertNull(service.authenticate("student", "Workshop123!"));
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT COUNT(*) FROM legacy_users")) {
            assertTrue(rows.next());
            assertEquals(2, rows.getInt(1));
        }
    }

    @Test
    void failsExplicitlyForMissingSeedConfiguration() throws Exception {
        for (Optional<String> password : java.util.List.of(Optional.<String>empty(), Optional.of(" "))) {
            String url = "jdbc:sqlite:" + TestDatabase.directory().resolve("no-seeds.db");
            PasswordHasher hasher = TestDatabase.hasher();
            LegacyDatabase database = new LegacyDatabase(url, hasher, new PasswordMigration(hasher),
                    password, Optional.empty());
            assertThrows(IllegalStateException.class, database::initialize);
        }
    }

    @Test
    void failsExplicitlyForUnusableDatabaseAndUnknownSchema() throws Exception {
        var directory = TestDatabase.directory();
        String unavailable = "jdbc:sqlite:" + directory.resolve("missing").resolve("auth.db");
        assertThrows(IllegalStateException.class, () -> TestDatabase.database(unavailable).initialize());
        String unknown = "jdbc:sqlite:" + directory.resolve("unknown.db");
        try (var connection = DriverManager.getConnection(unknown); var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE legacy_users(unexpected TEXT)");
        }
        assertThrows(IllegalStateException.class, () -> TestDatabase.database(unknown).initialize());
    }
}
