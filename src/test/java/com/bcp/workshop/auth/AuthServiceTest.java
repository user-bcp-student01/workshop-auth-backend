package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {
    @Test
    void authenticatesOnlyMatchingPasswordAndReturnsPublicIdentity() {
        var database = TestDatabase.database();
        database.initialize();
        AuthService service = new AuthService(database, TestDatabase.hasher());
        assertNull(service.authenticate("not-there", "wrong"));
        assertNull(service.authenticate("student", "wrong"));
        UserRecord user = service.authenticate("student", "Workshop123!");
        assertNotNull(user);
        assertEquals("STUDENT", user.role());
        assertNotNull(service.createToken(user));
        assertEquals("StoredCredentials[redacted]", database.findByUsername("student").toString());
    }
}
