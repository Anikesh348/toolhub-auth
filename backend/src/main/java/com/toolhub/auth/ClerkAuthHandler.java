package com.toolhub.auth;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.toolhub.config.ToolPolicy;
import com.toolhub.session.ToolHubSessionJwtProvider;
import com.toolhub.util.RedirectUtil;
import io.vertx.core.Handler;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.CookieSameSite;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.stream.Collectors;

public class ClerkAuthHandler implements Handler<RoutingContext> {

        private static final Logger log = LoggerFactory.getLogger(ClerkAuthHandler.class);

        private static final int SESSION_MAX_AGE_SECONDS = 3 * 24 * 60 * 60; // 3 days

        private final ClerkJwtVerifier clerkVerifier;

        public ClerkAuthHandler(ClerkJwtVerifier clerkVerifier) {
                this.clerkVerifier = clerkVerifier;
                log.info("ClerkAuthHandler initialized (Clerk → ToolHub session JWT)");
        }

        @Override
        public void handle(RoutingContext ctx) {

                ToolPolicy policy = ctx.get("policy");
                String path = ctx.request().path();

                // 1️⃣ Public / allowed paths
                if (!policy.authRequired || policy.isPathAllowed(path)) {
                        ctx.next();
                        return;
                }

                String token = null;
                TokenType tokenType = TokenType.NONE;

                // 2️⃣ Authorization header (API / automation)
                String authHeader = ctx.request().getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        token = authHeader.substring(7);
                        tokenType = TokenType.SESSION;
                }

                // 3️⃣ Clerk handoff JWT (one-time)
                if (token == null) {
                        String queryToken = ctx.request().getParam("handoff_jwt");
                        if (queryToken != null && !queryToken.isBlank()) {
                                token = queryToken;
                                tokenType = TokenType.CLERK;
                        }
                }

                // 4️⃣ Session cookie
                if (token == null) {
                        Cookie sessionCookie = ctx.request().getCookie("__toolhub_session");
                        if (sessionCookie != null) {
                                token = sessionCookie.getValue();
                                tokenType = TokenType.SESSION;
                        }
                }

                // 5️⃣ No token → redirect / 401
                if (token == null) {
                        handleUnauthenticated(ctx);
                        return;
                }

                // 6️⃣ Clerk JWT (handoff flow)
                if (tokenType == TokenType.CLERK) {
                        handleClerkJwt(ctx, token, policy);
                        return;
                }

                // 7️⃣ Session JWT (normal flow)
                handleSessionJwt(ctx, token, policy);
        }

        /*
         * =========================
         * Clerk JWT → session flow
         * =========================
         */

        private void handleClerkJwt(
                        RoutingContext ctx,
                        String clerkJwt,
                        ToolPolicy policy) {
                clerkVerifier.verify(clerkJwt, ar -> {
                        if (ar.failed()) {
                                log.warn("Clerk JWT verification failed: {}", ar.cause().getMessage());
                                handleUnauthenticated(ctx);
                                return;
                        }

                        JsonObject clerkClaims = ar.result();

                        if (!isRoleAllowed(policy, clerkClaims)) {
                                ctx.response().setStatusCode(403).end("Forbidden");
                                return;
                        }

                        // ✅ Mint ToolHub session JWT
                        String sessionToken = ToolHubSessionJwtProvider.generateSessionToken(
                                        clerkClaims.getString("sub"),
                                        clerkClaims.getString("role"),
                                        clerkClaims.getString("email"));

                        Cookie cookie = Cookie.cookie("__toolhub_session", sessionToken)
                                        .setHttpOnly(true)
                                        .setSecure(true)
                                        .setPath("/")
                                        .setMaxAge(SESSION_MAX_AGE_SECONDS)
                                        .setSameSite(CookieSameSite.LAX)
                                        .setDomain(".hostingfrompurva.xyz");

                        ctx.response().addCookie(cookie);

                        // Redirect to clean URL (remove handoff_jwt)
                        redirectToCleanUrl(ctx);
                });
        }

        /*
         * =========================
         * Session JWT flow
         * =========================
         */

        private void handleSessionJwt(
                        RoutingContext ctx,
                        String sessionToken,
                        ToolPolicy policy) {
                try {
                        DecodedJWT jwt = ToolHubSessionJwtProvider.verifySessionToken(sessionToken);

                        JsonObject sessionClaims = new JsonObject()
                                        .put("sub", jwt.getSubject())
                                        .put("role", jwt.getClaim("role").asString())
                                        .put("email", jwt.getClaim("email").asString());

                        ctx.put("authUser", sessionClaims);

                        if (!isRoleAllowed(policy, sessionClaims)) {
                                ctx.response().setStatusCode(403).end("Forbidden");
                                return;
                        }

                        ctx.next();

                } catch (Exception e) {
                        log.warn("Session JWT invalid or expired: {}", e.getMessage());
                        handleUnauthenticated(ctx);
                }
        }

        /*
         * =========================
         * Helpers
         * =========================
         */

        private boolean isRoleAllowed(ToolPolicy policy, JsonObject claims) {
                if (policy.role == null)
                        return true;
                String role = claims.getString("role");
                return role != null && policy.role.equalsIgnoreCase(role);
        }

        private void handleUnauthenticated(RoutingContext ctx) {
                if (isBrowserNavigation(ctx)) {
                        RedirectUtil.redirectToLogin(ctx);
                } else {
                        ctx.response()
                                        .setStatusCode(401)
                                        .putHeader("Content-Type", "application/json")
                                        .end("{\"error\":\"unauthenticated\"}");
                }
        }

        private boolean isBrowserNavigation(RoutingContext ctx) {
                String accept = ctx.request().getHeader("Accept");
                return accept != null && accept.contains("text/html");
        }

        private static void redirectToCleanUrl(RoutingContext ctx) {
                String path = ctx.request().path();
                String query = ctx.request().query();

                if (query != null && query.contains("handoff_jwt")) {
                        String cleaned = Arrays.stream(query.split("&"))
                                        .filter(p -> !p.startsWith("handoff_jwt="))
                                        .collect(Collectors.joining("&"));

                        path = cleaned.isEmpty() ? path : path + "?" + cleaned;
                }

                ctx.response()
                                .setStatusCode(302)
                                .putHeader("Location", path)
                                .end();
        }

        private enum TokenType {
                CLERK,
                SESSION,
                NONE
        }
}
