package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

@ApplicationScoped
public class PasswordMigration {
    private static final Set<String> LEGACY_COLUMNS =
            Set.of("id", "username", "password", "display_name", "role");
    private static final Set<String> HASH_COLUMNS =
            Set.of("id", "username", "password_hash", "display_name", "role");
    private static final String CREATE_TABLE = """
            CREATE TABLE legacy_users (
                id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE,
                password_hash TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)
            """;
    private final PasswordHasher hasher;

    @Inject
    public PasswordMigration(PasswordHasher hasher) {
        this.hasher = hasher;
    }

    public void migrate(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try {
            migrateSchema(connection);
            connection.commit();
        } catch (SQLException | RuntimeException error) {
            try {
                connection.rollback();
                connection.setAutoCommit(true);
            } catch (SQLException cleanupError) {
                error.addSuppressed(cleanupError);
            }
            throw error;
        }
        connection.setAutoCommit(true);
    }

    private void migrateSchema(Connection connection) throws SQLException {
        Set<String> columns = columns(connection);
        if (columns.isEmpty()) {
            execute(connection, CREATE_TABLE);
            return;
        }
        if (HASH_COLUMNS.equals(columns)) {
            return;
        }
        if (!LEGACY_COLUMNS.equals(columns)) {
            throw new SQLException("Unsupported authentication database schema");
        }
        execute(connection, CREATE_TABLE.replace("legacy_users", "hashed_users"));
        copyCredentials(connection);
        verifyCopy(connection);
        execute(connection, "DROP TABLE legacy_users");
        execute(connection, "ALTER TABLE hashed_users RENAME TO legacy_users");
    }

    private Set<String> columns(Connection connection) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("PRAGMA table_info(legacy_users)")) {
            while (rows.next()) {
                columns.add(rows.getString("name"));
            }
        }
        return columns;
    }

    private void copyCredentials(Connection connection) throws SQLException {
        try (var select = connection.createStatement();
             var rows = select.executeQuery("SELECT id,username,password,display_name,role FROM legacy_users");
             var insert = connection.prepareStatement("""
                     INSERT INTO hashed_users(id,username,password_hash,display_name,role) VALUES (?,?,?,?,?)
                     """)) {
            while (rows.next()) {
                insert.setLong(1, rows.getLong("id"));
                insert.setString(2, rows.getString("username"));
                insert.setString(3, hasher.hash(rows.getString("password")));
                insert.setString(4, rows.getString("display_name"));
                insert.setString(5, rows.getString("role"));
                insert.executeUpdate();
            }
        }
    }

    private void verifyCopy(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("""
                     SELECT
                       (SELECT COUNT(*) FROM legacy_users) = (SELECT COUNT(*) FROM hashed_users)
                       AND NOT EXISTS (
                         SELECT id,username,display_name,role FROM legacy_users
                         EXCEPT SELECT id,username,display_name,role FROM hashed_users)
                     """)) {
            if (!rows.next() || !rows.getBoolean(1)) {
                throw new SQLException("Authentication migration verification failed");
            }
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }
}
