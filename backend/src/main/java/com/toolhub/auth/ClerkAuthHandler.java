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

                // Tool does not require authentication
                if (!policy.authRequired) {
                        log.debug(
                                        "Auth not required [host={}, path={}]",
                                        ctx.request().host(),
                                        ctx.request().path());
                        ctx.next();
                        return;
                }

                String token = null;
                String tokenSource = null;
                
                String finalTokenSource = tokenSource;

                // 1️⃣ Authorization header (preferred)
                String authHeader = ctx.request().getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        token = authHeader.substring(7);
                        finalTokenSource = "Authorization header";
                }

                // 2️⃣ Query param handoff_jwt
                if (token == null) {
                        String queryToken = ctx.request().getParam("handoff_jwt");
                        if (queryToken != null && !queryToken.isBlank()) {
                                token = queryToken;
                                finalTokenSource = "query param (handoff_jwt)";
                        }
                }

                // 3️⃣ Cookie (optional / future)
                if (token == null) {
                        Cookie sessionCookie = ctx.request().getCookie("__session");
                        if (sessionCookie != null) {
                                token = sessionCookie.getValue();
                                finalTokenSource = "cookie (__session)";
                        }
                }

                // No token → redirect to login
                if (token == null) {
                        log.info(
                                        "No auth token found, redirecting to login " +
                                                        "[method={}, host={}, path={}]",
                                        ctx.request().method(),
                                        ctx.request().host(),
                                        ctx.request().path());
                        RedirectUtil.redirectToLogin(ctx);
                        return;
                }

                log.info(
                                "Verifying JWT from {} [method={}, host={}, path={}]",
                                finalTokenSource,
                                ctx.request().method(),
                                ctx.request().host(),
                                ctx.request().path());

                verifier.verify(token, ar -> {
                        if (ar.failed()) {
                                log.warn(
                                                "JWT verification failed " +
                                                                "[source={}, host={}, path={}, reason={}]",
                                                tokenSource,
                                                ctx.request().host(),
                                                ctx.request().path(),
                                                ar.cause().getMessage());
                                ctx.response().setStatusCode(401).end("Invalid token");
                                return;
                        }

                        var claims = ar.result();
                        ctx.put("authUser", claims);

                        log.info(
                                        "JWT verified successfully [sub={}, source={}]",
                                        claims.getString("sub"),
                                        tokenSource);

                        // Role-based authorization (if required)
                        if (policy.role != null) {
                                String role = claims.getString("role");

                                if (role == null || !policy.role.equalsIgnoreCase(role)) {
                                        log.warn(
                                                        "Access denied due to role mismatch " +
                                                                        "[required={}, actual={}, host={}, path={}]",
                                                        policy.role,
                                                        role,
                                                        ctx.request().host(),
                                                        ctx.request().path());
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
}
