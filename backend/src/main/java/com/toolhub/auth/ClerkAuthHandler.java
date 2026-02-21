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

public class ClerkAuthHandler implements Handler<RoutingContext> {

        private static final Logger log = LoggerFactory.getLogger(ClerkAuthHandler.class);

        private static final int SESSION_MAX_AGE_SECONDS = 3 * 24 * 60 * 60; // 3 days

        private final ClerkJwtVerifier clerkVerifier;

        public ClerkAuthHandler(ClerkJwtVerifier clerkVerifier) {
                this.clerkVerifier = clerkVerifier;
                log.info("ClerkAuthHandler initialized (generic tool gateway)");
        }

        @Override
        public void handle(RoutingContext ctx) {
                log.info("Root Auth Handler");
                ToolPolicy policy = ctx.get("policy");
                String path = ctx.request().path();

                // 1️⃣ Allow explicitly whitelisted paths
                if (!policy.authRequired || policy.isPathAllowed(path)) {
                        ctx.next();
                        return;
                }

                String token = null;
                TokenType tokenType = TokenType.NONE;

                // 2️⃣ Authorization header (automation / API)
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

                // 5️⃣ No token → login
                if (token == null) {
                        handleUnauthenticated(ctx);
                        return;
                }

                // 6️⃣ Clerk → Session mint
                if (tokenType == TokenType.CLERK) {
                        handleClerkJwt(ctx, token, policy);
                        return;
                }

                // 7️⃣ Session validation
                handleSessionJwt(ctx, token, policy);
        }

        /*
         * =========================
         * Clerk JWT → Session flow
         * =========================
         */

        private void handleClerkJwt(
                        RoutingContext ctx,
                        String clerkJwt,
                        ToolPolicy policy) {
                clerkVerifier.verify(clerkJwt, ar -> {
                        if (ar.failed()) {
                                log.warn("Clerk JWT verification failed: {}",
                                                ar.cause().getMessage());
                                handleUnauthenticated(ctx);
                                return;
                        }

                        JsonObject clerkClaims = ar.result();

                        if (!isRoleAllowed(policy, clerkClaims)) {
                                ctx.response().setStatusCode(403).end("Forbidden");
                                return;
                        }

                        // 🔐 Mint session JWT
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

                        // ✅ CRITICAL CHANGE:
                        // Redirect to tool target (used by Caddy)
                        String target = policy.target;

                        log.info(
                                        "Auth complete for user={}, redirecting to tool target={}",
                                        clerkClaims.getString("sub"),
                                        target);

                        ctx.response()
                                        .setStatusCode(302)
                                        .putHeader("Location", target)
                                        .end();
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

                        if (!isRoleAllowed(policy, sessionClaims) || !isAllowedEmail(policy, sessionClaims)) {
                                ctx.response().setStatusCode(403).end("Forbidden");
                                return;
                        }

                        ctx.response().setStatusCode(200).end();

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

        private boolean isRoleAllowed(
                        ToolPolicy policy,
                        JsonObject claims) {
                if (policy.role == null)
                        return true;
                String role = claims.getString("role");
                return role != null &&
                                policy.role.equalsIgnoreCase(role);
        }

        private boolean isAllowedEmail(ToolPolicy policy, JsonObject claims) {
                if (policy.allowedEmails == null) {
                        return true;
                }
                String email = claims.getString("email");
                log.info("logging the email id from claims: {}", email);
                if (email != null && policy.allowedEmails.contains(email)) {
                        log.debug("{}: is allowed, forwarding..." , email);
                }
                return false;
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

        private enum TokenType {
                CLERK,
                SESSION,
                NONE
        }
}
