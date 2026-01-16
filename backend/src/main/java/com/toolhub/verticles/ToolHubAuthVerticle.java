package com.toolhub.verticles;

import com.toolhub.auth.ClerkAuthHandler;
import com.toolhub.auth.ClerkJwtVerifier;
import com.toolhub.config.AppConfig;
import com.toolhub.config.ToolPolicy;
import com.toolhub.config.ToolPolicyLoader;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;

import com.toolhub.proxy.ReverseProxyHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ToolHubAuthVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(ToolHubAuthVerticle.class);

    @Override
    public void start() {
        log.info("Starting ToolHub Auth Gateway");

        Map<String, ToolPolicy> policies;
        try {
            policies = ToolPolicyLoader.load("tools.yml");
            log.info("Loaded {} tool policies", policies.size());
        } catch (Exception e) {
            log.error("Failed to load tools.yml configuration", e);
            throw e;
        }

        Router router = Router.router(vertx);

        // Resolve tool policy by Host header
        router.route().handler(ctx -> {
            String host = ctx.request().host();
            ToolPolicy policy = policies.get(host);

            if (policy == null) {
                log.warn(
                        "No tool policy found for host='{}' [method={}, path={}]",
                        host,
                        ctx.request().method(),
                        ctx.request().path());
                ctx.response().setStatusCode(404).end();
                return;
            }

            log.info(
                    "Resolved tool policy for host='{}' → target='{}'",
                    host,
                    policy.target);

            ctx.put("policy", policy);
            ctx.next();
        });

        // JWT verifier (JWKS cached in-memory)
        ClerkJwtVerifier verifier = new ClerkJwtVerifier(vertx);
        log.info("Clerk JWT verifier initialized");
        int port = Integer.parseInt(AppConfig.HTTP_PORT);

        WebClient webClient = WebClient.create(vertx);
        HttpClientOptions options = new HttpClientOptions()
                .setKeepAlive(true)
                .setIdleTimeout(0) // 🔑 DO NOT AUTO-CLOSE
                .setConnectTimeout(5000)
                .setTcpKeepAlive(true);

        HttpClient httpClient = vertx.createHttpClient(options);

        router.route()
                .handler(new ClerkAuthHandler(verifier))
                .handler(new ReverseProxyHandler(webClient, httpClient));

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(port, ar -> {
                    if (ar.succeeded()) {
                        log.info("Auth Gateway listening on port 8082");
                    } else {
                        log.error("Failed to start HTTP server on port 8082", ar.cause());
                    }
                });
    }
}
