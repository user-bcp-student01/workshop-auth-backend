package com.bcp.workshop.auth;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;

@Startup
@ApplicationScoped
public class CorsConfiguration {
    @Inject
    public CorsConfiguration(@ConfigProperty(name = "auth.cors.origins") String origins) {
        for (String origin : origins.split(",", -1)) {
            validateOrigin(origin.trim());
        }
    }

    private void validateOrigin(String origin) {
        URI uri = URI.create(origin);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null
                || !uri.getPath().isEmpty() || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("CORS origins must be explicit HTTP(S) origins without paths");
        }
    }
}
