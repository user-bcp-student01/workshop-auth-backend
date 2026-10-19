package com.bcp.workshop.auth;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.runtime.Startup;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@ApplicationScoped
@Startup
public class LegacyDatabase {
    private static final Logger LOG = Logger.getLogger(LegacyDatabase.class);
    private static final String FIND_USER =
            "SELECT id,username,password_hash,display_name,role FROM legacy_users WHERE username=?";
    private final String databaseUrl;
    private final PasswordHasher hasher;
    private final PasswordMigration migration;
    private final Optional<String> studentPassword;
    private final Optional<String> trainerPassword;

    @Inject
    public LegacyDatabase(@ConfigProperty(name = "auth.db.url") String databaseUrl,
                          PasswordHasher hasher, PasswordMigration migration,
                          @ConfigProperty(name = "auth.seed.student-password") Optional<String> studentPassword,
                          @ConfigProperty(name = "auth.seed.trainer-password") Optional<String> trainerPassword) {
        this.databaseUrl = databaseUrl;
        this.hasher = hasher;
        this.migration = migration;
        this.studentPassword = studentPassword;
        this.trainerPassword = trainerPassword;
    }

    @PostConstruct
    void initialize() {
        try (Connection connection = DriverManager.getConnection(databaseUrl)) {
            migration.migrate(connection);
            seedUser(connection, "student", studentPassword, "Workshop Student", "STUDENT");
            seedUser(connection, "trainer", trainerPassword, "Workshop Trainer", "TRAINER");
        } catch (SQLException | RuntimeException error) {
            LOG.error("Unable to initialize authentication database", error);
            throw new IllegalStateException("Authentication database initialization failed", error);
        }
    }

    StoredCredentials findByUsername(String username) {
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             var statement = connection.prepareStatement(FIND_USER)) {
            statement.setString(1, username);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? mapCredentials(rows) : null;
            }
        } catch (SQLException error) {
            // Preserve the documented 401 on query failure until H-05 is authorized.
            LOG.error("Unable to query authentication database", error);
            return null;
        }
    }

    private StoredCredentials mapCredentials(ResultSet rows) throws SQLException {
        UserRecord user = new UserRecord(rows.getLong("id"), rows.getString("username"),
                rows.getString("display_name"), rows.getString("role"));
        return new StoredCredentials(user, rows.getString("password_hash"));
    }

    private void seedUser(Connection connection, String username, Optional<String> password,
                          String displayName, String role) throws SQLException {
        if (exists(connection, username)) {
            return;
        }
        String requiredPassword = password.filter(value -> !value.isBlank()).orElseThrow(
                () -> new IllegalStateException("Configure seed password for missing laboratory user: " + username));
        try (var statement = connection.prepareStatement("""
                INSERT OR IGNORE INTO legacy_users(username,password_hash,display_name,role) VALUES (?,?,?,?)
                """)) {
            statement.setString(1, username);
            statement.setString(2, hasher.hash(requiredPassword));
            statement.setString(3, displayName);
            statement.setString(4, role);
            statement.executeUpdate();
        }
    }

    private boolean exists(Connection connection, String username) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT 1 FROM legacy_users WHERE username=?")) {
            statement.setString(1, username);
            try (var rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }
}
