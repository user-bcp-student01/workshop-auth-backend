package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {
    private static final UserRecord IDENTITY = new UserRecord(42, " exact user ", "Visible", "STUDENT");
    private static final String ORIGINAL_PASSWORD = " original password ";
    private static final String STORED_HASH = "test-double-hash-not-for-storage";

    @Test
    void missingUserDoesNotVerifyPassword() {
        var hasher = new Verification(false);
        var lookup = new Lookup(hasher, null);
        var service = new AuthService(lookup, hasher, TestDatabase.tokens());

        assertNull(service.authenticate(IDENTITY.username(), ORIGINAL_PASSWORD));
        assertEquals(1, lookup.calls);
        assertEquals(IDENTITY.username(), lookup.username);
        assertEquals(0, hasher.calls);
    }

    @Test
    void wrongPasswordVerifiesOriginalOnceAndReturnsNoIdentity() {
        var hasher = new Verification(false);
        var lookup = new Lookup(hasher, new StoredCredentials(IDENTITY, STORED_HASH));
        var service = new AuthService(lookup, hasher, TestDatabase.tokens());

        assertNull(service.authenticate(IDENTITY.username(), ORIGINAL_PASSWORD));
        assertDelegation(lookup, hasher);
    }

    @Test
    void matchingPasswordReturnsOnlyThePublicIdentity() {
        var hasher = new Verification(true);
        var lookup = new Lookup(hasher, new StoredCredentials(IDENTITY, STORED_HASH));
        var service = new AuthService(lookup, hasher, TestDatabase.tokens());

        assertSame(IDENTITY, service.authenticate(IDENTITY.username(), ORIGINAL_PASSWORD));
        assertDelegation(lookup, hasher);
    }

    private void assertDelegation(Lookup lookup, Verification hasher) {
        assertEquals(1, lookup.calls);
        assertEquals(IDENTITY.username(), lookup.username);
        assertEquals(1, hasher.calls);
        assertEquals(ORIGINAL_PASSWORD, hasher.password);
        assertEquals(STORED_HASH, hasher.hash);
    }

    private static class Lookup extends LegacyDatabase {
        private final StoredCredentials credentials;
        private int calls;
        private String username;

        Lookup(PasswordHasher hasher, StoredCredentials credentials) {
            super("jdbc:sqlite:unused-test-double", hasher, new PasswordMigration(hasher),
                    java.util.Optional.empty(), java.util.Optional.empty());
            this.credentials = credentials;
        }

        @Override
        StoredCredentials findByUsername(String username) {
            calls++;
            this.username = username;
            return credentials;
        }
    }

    private static class Verification extends PasswordHasher {
        private final boolean matches;
        private int calls;
        private String password;
        private String hash;

        Verification(boolean matches) {
            super(MIN_ITERATIONS);
            this.matches = matches;
        }

        @Override
        public boolean verify(String password, String encodedHash) {
            calls++;
            this.password = password;
            this.hash = encodedHash;
            return matches;
        }
    }

    @Test
    void authenticatesOnlyMatchingPasswordAndReturnsPublicIdentity() {
        var database = TestDatabase.database();
        database.initialize();
        AuthService service = new AuthService(database, TestDatabase.hasher(), TestDatabase.tokens());
        assertNull(service.authenticate("not-there", "wrong"));
        assertNull(service.authenticate("student", "wrong"));
        UserRecord user = service.authenticate("student", "Workshop123!");
        assertNotNull(user);
        assertEquals("STUDENT", user.role());
        assertNotNull(service.createToken(user));
        assertEquals("StoredCredentials[redacted]", database.findByUsername("student").toString());
    }
}
