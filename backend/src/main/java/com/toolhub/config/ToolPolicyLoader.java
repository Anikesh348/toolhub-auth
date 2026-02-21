package com.toolhub.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ToolPolicyLoader {

    private static final Logger log = LoggerFactory.getLogger(ToolPolicyLoader.class);

    @SuppressWarnings("unchecked")
    public static Map<String, ToolPolicy> load(String resourceName) {
        log.info("Loading tool policies from resource '{}'", resourceName);

        InputStream is = ToolPolicyLoader.class
                .getClassLoader()
                .getResourceAsStream(resourceName);

        if (is == null) {
            log.error("Tool policy resource '{}' not found on classpath", resourceName);
            throw new IllegalStateException(
                    "Could not find " + resourceName + " on classpath");
        }

        Yaml yaml = new Yaml();
        Map<String, Object> root;

        try {
            root = yaml.load(is);
        } catch (Exception e) {
            log.error("Failed to parse YAML from '{}'", resourceName, e);
            throw e;
        }

        if (root == null || !root.containsKey("tools")) {
            log.error("Invalid tools.yml: missing top-level 'tools' key");
            throw new IllegalStateException(
                    "Invalid tools.yml: missing 'tools' section");
        }

        Map<String, Object> tools = (Map<String, Object>) root.get("tools");
        Map<String, ToolPolicy> policies = new HashMap<>();

        tools.forEach((key, value) -> {
            try {
                Map<String, Object> tool = (Map<String, Object>) value;
                Map<String, Object> auth = (Map<String, Object>) tool.get("auth");

                ToolPolicy p = new ToolPolicy();
                p.host = (String) tool.get("host");
                p.target = (String) tool.get("target");

                p.authRequired = auth != null && Boolean.TRUE.equals(auth.get("required"));
                p.role = auth != null ? (String) auth.get("role") : null;
                p.allowedEmails = auth != null 
                ? (auth.get("allowedEmails") != null ? (Set<String>) auth.get("allowedEmails") : null )
                : null;
                
                if (auth != null && auth.containsKey("allowPaths")) {
                    p.allowPaths = (List<String>) auth.get("allowPaths");
                } else {
                    p.allowPaths = Collections.emptyList();
                }

                policies.put(p.host, p);

                log.info(
                        "Loaded tool policy '{}' [host={}, target={}, authRequired={}, role={}, allowPaths={}]",
                        key,
                        p.host,
                        p.target,
                        p.authRequired,
                        p.role,
                        p.allowPaths);
            } catch (Exception e) {
                log.error(
                        "Failed to load tool policy '{}' from tools.yml",
                        key,
                        e);
                throw e;
            }
        });

        log.info("Successfully loaded {} tool policies", policies.size());
        return policies;
    }
}
