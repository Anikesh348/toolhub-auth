package com.toolhub.auth;

import com.toolhub.config.ToolPolicy;
import com.toolhub.util.RedirectUtil;
import io.vertx.core.Handler;
import io.vertx.core.http.Cookie;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClerkAuthHandler implements Handler<RoutingContext> {

        private static final Logger log = LoggerFactory.getLogger(ClerkAuthHandler.class);

        private final ClerkJwtVerifier verifier;

        public ClerkAuthHandler(ClerkJwtVerifier verifier) {
                this.verifier = verifier;
                log.info("ClerkAuthHandler initialized (OPTION B – no session)");
        }

        @Override
        public void handle(RoutingContext ctx) {
                ToolPolicy policy = ctx.get("policy");
                String path = ctx.request().path();

                // 1️⃣ Allow internal / streaming paths early
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
                boolean tokenFromQuery = false;

                // 3️⃣ Authorization header
                String authHeader = ctx.request().getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        token = authHeader.substring(7);
                        tokenSource = "Authorization header";
                }

                // 4️⃣ Query param handoff_jwt
                if (token == null) {
                        String queryToken = ctx.request().getParam("handoff_jwt");
                        if (queryToken != null && !queryToken.isBlank()) {
                                token = queryToken;
                                tokenSource = "query param (handoff_jwt)";
                                tokenFromQuery = true;
                        }
                }

                // 5️⃣ Cookie (not used in Option B, but harmless)
                if (token == null) {
                        Cookie sessionCookie = ctx.request().getCookie("__session");
                        if (sessionCookie != null) {
                                token = sessionCookie.getValue();
                                tokenSource = "cookie (__session)";
                        }
                }

                // 6️⃣ No token → redirect or 401
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
                final boolean finalTokenFromQuery = tokenFromQuery;

                log.info(
                                "Verifying JWT [source={}, host={}, path={}]",
                                finalTokenSource,
                                ctx.request().host(),
                                path);

                verifier.verify(finalToken, ar -> {
                        if (ar.failed()) {
                                log.warn(
                                                "JWT verification failed [source={}, reason={}]",
                                                finalTokenSource,
                                                ar.cause().getMessage());
                                ctx.response().setStatusCode(401).end("Invalid token");
                                return;
                        }

                        var claims = ar.result();
                        ctx.put("authUser", claims);

                        log.info(
                                        "JWT verified successfully [sub={}, source={}]",
                                        claims.getString("sub"),
                                        finalTokenSource);

                        // 7️⃣ Role check
                        if (policy.role != null) {
                                String role = claims.getString("role");
                                if (role == null || !policy.role.equalsIgnoreCase(role)) {
                                        ctx.response().setStatusCode(403).end("Forbidden");
                                        return;
                                }
                        }

                        // 🔴 OPTION B CORE LOGIC
                        if (finalTokenFromQuery) {
                                log.info("JWT came from query → allowing request without redirect");
                                ctx.next(); // ⬅️ THIS STOPS THE LOOP
                                return;
                        }

                        ctx.next();
                });
        }

        private boolean isBrowserNavigation(RoutingContext ctx) {
                String accept = ctx.request().getHeader("Accept");
                return accept != null && accept.contains("text/html");
        }
}
