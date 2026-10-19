package com.bcp.workshop.auth;

record StoredCredentials(UserRecord user, String passwordHash) {
    @Override
    public String toString() {
        return "StoredCredentials[redacted]";
    }
}
