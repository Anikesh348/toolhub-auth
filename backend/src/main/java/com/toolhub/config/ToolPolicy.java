package com.toolhub.config;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ToolPolicy {

    public String host;
    public String target;

    public boolean authRequired;
    public String role;
    public Set<String> allowedEmails = new HashSet<>();

    /**
     * Paths that are allowed once the user is authenticated.
     * Supports:
     * - exact match (/)
     * - prefix match (/api/*)
     */
    public List<String> allowPaths = Collections.emptyList();

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

}
