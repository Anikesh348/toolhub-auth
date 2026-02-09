package com.toolhub.config;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import io.vertx.core.http.HttpServerRequest; // Ensure this import is added

public class ToolPolicy {

    public String host;
    public String target;
    public boolean authRequired;
    public String role;

    // --- NEW FIELD FOR HEADERS ---
    public List<HeaderRule> allowedHeaders = Collections.emptyList();

    public static class HeaderRule {
        public String name;
        public String contains;
    }
    // -----------------------------

    public List<String> allowPaths = Collections.emptyList();
    public Set<String> allowedEmails;

    /**
     * Checks if the request contains specific headers that allow bypassing 
     * the outer authentication (e.g., Jellyfin's own Auth Token).
     */
    public boolean isHeaderAllowed(HttpServerRequest request) {
        if (allowedHeaders == null || allowedHeaders.isEmpty()) {
            return false;
        }

        for (HeaderRule rule : allowedHeaders) {
            String headerValue = request.getHeader(rule.name);
            if (headerValue != null && headerValue.contains(rule.contains)) {
                return true;
            }
        }
        return false;
    }

    public boolean isPathAllowed(String requestPath) {
        if (allowPaths == null || allowPaths.isEmpty()) {
            return false;
        }

        for (String allowed : allowPaths) {
            if (allowed.equals(requestPath)) {
                return true;
            }

            if (allowed.endsWith("/*")) {
                String prefix = allowed.substring(0, allowed.length() - 2);
                if (requestPath.startsWith(prefix + "/")) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isEmailAllowed(String email) {
        if (allowedEmails == null || allowedEmails.isEmpty()) {
            return true;
        }
        if (email == null) {
            return false;
        }
        return allowedEmails.contains(email.toLowerCase());
    }

    public void normalize() {
        if (allowedEmails != null) {
            allowedEmails = allowedEmails.stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());
        }
    }
}