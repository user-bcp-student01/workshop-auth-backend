package com.bcp.workshop.auth;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;

final class TestDatabase {
    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private TestDatabase() {}

    static Connection connection() throws SQLException {
        return DriverManager.getConnection(System.getenv("AUTH_DB_URL"));
    }

    static Path directory() throws Exception {
        Path root = Path.of("target", "test-data");
        Files.createDirectories(root);
        return Files.createTempDirectory(root, "auth-");
    }

    static PasswordHasher hasher() {
        return new PasswordHasher(PasswordHasher.MIN_ITERATIONS);
    }

    static LegacyDatabase database() {
        return database(System.getenv("AUTH_DB_URL"));
    }

    static LegacyDatabase database(String url) {
        PasswordHasher hasher = hasher();
        return new LegacyDatabase(url, hasher, new PasswordMigration(hasher),
                Optional.of("Workshop123!"), Optional.of("Trainer123!"));
    }

    static AuthService service() {
        return new AuthService(database(), hasher());
    }

    static void insert(String username, String password, String displayName, String role) throws Exception {
        try (Connection connection = connection();
             var statement = connection.prepareStatement(
                     "INSERT OR REPLACE INTO legacy_users(username,password_hash,display_name,role) VALUES (?,?,?,?)")) {
            statement.setString(1, username);
            statement.setString(2, hasher().hash(password));
            statement.setString(3, displayName);
            statement.setString(4, role);
            statement.executeUpdate();
        }
    }
}
