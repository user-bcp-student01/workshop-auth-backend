package com.bcp.workshop.auth;

import java.sql.SQLException;

public class AuthenticationPersistenceException extends RuntimeException {
    public AuthenticationPersistenceException(SQLException cause) {
        super("Unable to query authentication database", cause);
    }
}
