package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AuthService {
    private final LegacyDatabase database;
    private final PasswordHasher hasher;

    @Inject
    public AuthService(LegacyDatabase database, PasswordHasher hasher) {
        this.database = database;
        this.hasher = hasher;
    }

    public UserRecord authenticate(String username, String password) {
        StoredCredentials credentials = database.findByUsername(username);
        if (credentials == null || !hasher.verify(password, credentials.passwordHash())) {
            return null;
        }
        return credentials.user();
    }

    String createToken(UserRecord user) {
        return LegacyTokenUtil.create(user.username(), user.role());
    }
}
