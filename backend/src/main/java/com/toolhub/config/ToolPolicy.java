package com.toolhub.config;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ToolPolicy {

    public String host;
    public String target;

    public boolean authRequired;
    public String role;

    /**
     * Paths that are allowed once the user is authenticated.
     * Supports:
     * - exact match (/)
     * - prefix match (/api/*)
     */
    public List<String> allowPaths = Collections.emptyList();

    /**
     * Allowed email addresses for this tool.
     * If empty or null → no email restriction.
     */
    public Set<String> allowedEmails;

    public boolean isPathAllowed(String requestPath) {
        if (allowPaths == null || allowPaths.isEmpty()) {
            return false;
        }

        for (String allowed : allowPaths) {
            // Exact match
            if (allowed.equals(requestPath)) {
                return true;
            }

            // Prefix match: /api/*
            if (allowed.endsWith("/*")) {
                String prefix = allowed.substring(0, allowed.length() - 2);
                if (requestPath.startsWith(prefix + "/")) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Check if an email is allowed to access this tool.
     * - If no allowlist is defined → allow all
     * - Case-insensitive match
     */
    public boolean isEmailAllowed(String email) {
        if (allowedEmails == null || allowedEmails.isEmpty()) {
            return true;
        }

        if (email == null) {
            return false;
        }

        return allowedEmails.contains(email.toLowerCase());
    }

    /**
     * Normalize emails after loading config (optional but recommended)
     */
    public void normalize() {
        if (allowedEmails != null) {
            allowedEmails = allowedEmails.stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());
        }
    }
}
