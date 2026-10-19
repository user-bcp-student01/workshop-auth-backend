package com.bcp.workshop.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.commons.text.StringEscapeUtils;
import org.jboss.logging.Logger;

import java.util.Map;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    private static final Logger LOG = Logger.getLogger(AuthResource.class);
    private static final String LEGACY_DEBUG = "authenticated-with-legacy-flow";

    @Inject
    AuthService service;

    @POST
    @Path("/login")
    public Response login(LoginRequest request) {
        String validationMessage = validate(request);
        if (validationMessage != null) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("message", validationMessage)).build();
        }
        try {
            UserRecord user = service.authenticate(request.username, request.password);
            if (user == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(Map.of("message", "Invalid user " + request.username + " or password")).build();
            }
            return Response.ok(success(user)).build();
        } catch (Exception error) {
            LOG.error("Authentication request failed", error);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Map.of(
                    "message", error.getMessage() == null ? "Unknown error" : error.getMessage(),
                    "exception", error.getClass().getName())).build();
        }
    }

    private String validate(LoginRequest request) {
        if (request == null) {
            return "Request is null";
        }
        if (request.username == null || request.username.trim().isEmpty()) {
            return "Username is required";
        }
        if (request.password == null || request.password.trim().isEmpty()) {
            return "Password is required";
        }
        return null;
    }

    private LoginResponse success(UserRecord user) {
        UserResponse publicUser = new UserResponse(user.username(),
                StringEscapeUtils.escapeHtml4(user.displayName()), user.role());
        return new LoginResponse(service.createToken(user), LegacyTokenUtil.EXPIRATION_SECONDS, publicUser, LEGACY_DEBUG);
    }

    public record UserResponse(String username, String displayName, String role) {}

    public record LoginResponse(String token, int expiresIn, UserResponse user, String debug) {}
}
