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
                log.info("ClerkAuthHandler initialized");
        }

        @Override
        public void handle(RoutingContext ctx) {
                ToolPolicy policy = ctx.get("policy");
                String path = ctx.request().path();

                // 1️⃣ Allow internal paths early (API / assets / ws)
                if (policy.isPathAllowed(path)) {
                        log.info(
                                        "Path allowed without auth [host={}, path={}]",
                                        ctx.request().host(),
                                        path);
                        ctx.next();
                        return;
                }

                // 2️⃣ Tool does not require authentication
                if (!policy.authRequired) {
                        log.debug(
                                        "Auth not required [host={}, path={}]",
                                        ctx.request().host(),
                                        path);
                        ctx.next();
                        return;
                }

                String token = null;
                String tokenSource = null;

                // 3️⃣ Authorization header (preferred)
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
                        }
                }

                // 5️⃣ Cookie (__session) – optional
                if (token == null) {
                        Cookie sessionCookie = ctx.request().getCookie("__session");
                        if (sessionCookie != null) {
                                token = sessionCookie.getValue();
                                tokenSource = "cookie (__session)";
                        }
                }

                // 6️⃣ No token → redirect (browser) or 401 (API)
                if (token == null) {
                        if (isBrowserNavigation(ctx)) {
                                log.info(
                                                "Unauthenticated browser request → redirecting [host={}, path={}]",
                                                ctx.request().host(),
                                                path);
                                RedirectUtil.redirectToLogin(ctx);
                        } else {
                                ctx.response()
                                                .setStatusCode(401)
                                                .putHeader("Content-Type", "application/json")
                                                .end("{\"error\":\"unauthenticated\"}");
                        }
                        return;
                }

                // 🔒 Freeze variables for async lambda
                final String finalToken = token;
                final String finalTokenSource = tokenSource;

                log.info(
                                "Verifying JWT [source={}, method={}, host={}, path={}]",
                                finalTokenSource,
                                ctx.request().method(),
                                ctx.request().host(),
                                path);

                verifier.verify(finalToken, ar -> {
                        if (ar.failed()) {
                                log.warn(
                                                "JWT verification failed " +
                                                                "[source={}, host={}, path={}, reason={}]",
                                                finalTokenSource,
                                                ctx.request().host(),
                                                path,
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

                        // 7️⃣ Role-based authorization
                        if (policy.role != null) {
                                String role = claims.getString("role");

                                if (role == null || !policy.role.equalsIgnoreCase(role)) {
                                        log.warn(
                                                        "Access denied (role mismatch) " +
                                                                        "[required={}, actual={}, host={}, path={}]",
                                                        policy.role,
                                                        role,
                                                        ctx.request().host(),
                                                        path);
                                        ctx.response().setStatusCode(403).end("Forbidden");
                                        return;
                                }

                                log.info(
                                                "Role check passed [role={}, host={}]",
                                                role,
                                                ctx.request().host());
                        }

                        ctx.next();
                });
        }

        private boolean isBrowserNavigation(RoutingContext ctx) {
                String accept = ctx.request().getHeader("Accept");
                return accept != null && accept.contains("text/html");
        }
}
