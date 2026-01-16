package com.toolhub.auth;

import com.toolhub.config.ToolPolicy;
import com.toolhub.util.RedirectUtil;
import io.vertx.core.Handler;
import io.vertx.core.http.Cookie;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class ClerkAuthHandler implements Handler<RoutingContext> {

        private static final Logger log = LoggerFactory.getLogger(ClerkAuthHandler.class);

        private final ClerkJwtVerifier verifier;

        public ClerkAuthHandler(ClerkJwtVerifier verifier) {
                this.verifier = verifier;
                log.info("ClerkAuthHandler initialized");
        }

        @Override
        public void handle(RoutingContext ctx) {
                ToolPolicy policy = ctx.get("policy");
                String path = ctx.request().path();

                // 1️⃣ Allow internal paths early
                if (policy.isPathAllowed(path)) {
                        ctx.next();
                        return;
                }

                // 2️⃣ Tool does not require authentication
                if (!policy.authRequired) {
                        ctx.next();
                        return;
                }

                String token = null;
                String tokenSource = null;

                // 3️⃣ Authorization header
                String authHeader = ctx.request().getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        token = authHeader.substring(7);
                        tokenSource = "Authorization header";
                }

                // 4️⃣ Query param handoff_jwt
                AtomicBoolean tokenFromQuery = new AtomicBoolean(false);
                if (token == null) {
                        String queryToken = ctx.request().getParam("handoff_jwt");
                        if (queryToken != null && !queryToken.isBlank()) {
                                token = queryToken;
                                tokenSource = "query param (handoff_jwt)";
                                tokenFromQuery.set(true);
                        }
                }

                // 5️⃣ Cookie (__session)
                if (token == null) {
                        Cookie sessionCookie = ctx.request().getCookie("__session");
                        if (sessionCookie != null) {
                                token = sessionCookie.getValue();
                                tokenSource = "cookie (__session)";
                        }
                }

                // 6️⃣ No token
                if (token == null) {
                        if (isBrowserNavigation(ctx)) {
                                RedirectUtil.redirectToLogin(ctx);
                        } else {
                                ctx.response()
                                                .setStatusCode(401)
                                                .putHeader("Content-Type", "application/json")
                                                .end("{\"error\":\"unauthenticated\"}");
                        }
                        return;
                }

                final String finalToken = token;
                final String finalTokenSource = tokenSource;

                log.info(
                                "Verifying JWT [source={}, host={}, path={}]",
                                finalTokenSource,
                                ctx.request().host(),
                                path);

                verifier.verify(finalToken, ar -> {
                        if (ar.failed()) {
                                log.warn(
                                                "JWT verification failed [source={}, path={}]",
                                                finalTokenSource,
                                                path);
                                ctx.response().setStatusCode(401).end("Invalid token");
                                return;
                        }

                        var claims = ar.result();
                        ctx.put("authUser", claims);

                        log.info(
                                        "JWT verified [sub={}, source={}]",
                                        claims.getString("sub"),
                                        finalTokenSource);

                        // 🔑 REMOVE JWT FROM URL (important)
                        if (tokenFromQuery.get() && isBrowserNavigation(ctx)) {
                                String cleanUrl = buildCleanUrl(ctx);
                                log.info("Redirecting to clean URL (JWT stripped)");
                                ctx.response()
                                                .setStatusCode(302)
                                                .putHeader("Location", cleanUrl)
                                                .end();
                                return;
                        }

                        // 7️⃣ Role check
                        if (policy.role != null) {
                                String role = claims.getString("role");
                                if (role == null || !policy.role.equalsIgnoreCase(role)) {
                                        ctx.response().setStatusCode(403).end("Forbidden");
                                        return;
                                }
                        }

                        ctx.next();
                });
        }

        private boolean isBrowserNavigation(RoutingContext ctx) {
                String accept = ctx.request().getHeader("Accept");
                return accept != null && accept.contains("text/html");
        }

        /**
         * Rebuild URL without handoff_jwt
         */
        private String buildCleanUrl(RoutingContext ctx) {
                String base = ctx.request().scheme() + "://" +
                                ctx.request().host() +
                                ctx.request().path();

                String query = ctx.request().params().entries().stream()
                                .filter(e -> !"handoff_jwt".equals(e.getKey()))
                                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) +
                                                "=" +
                                                URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                                .collect(Collectors.joining("&"));

                return query.isEmpty() ? base : base + "?" + query;
        }
}
