package com.toolhub.auth;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.toolhub.config.ToolPolicy;
import com.toolhub.session.ToolHubSessionJwtProvider;
import io.vertx.core.Handler;
import io.vertx.core.http.Cookie;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthCheckHandler implements Handler<RoutingContext> {

    private static final Logger log = LoggerFactory.getLogger(AuthCheckHandler.class);

    @Override
    public void handle(RoutingContext ctx) {
        log.info("GET /internal/auth/check");

        ToolPolicy policy = ctx.get("policy");

        String path = ctx.request().path();

        if (!policy.authRequired || policy.isPathAllowed(path) || policy.isHeaderAllowed(ctx.request())) {
                ctx.next();
                return;
        }

        if (policy == null) {
            log.error("AuthCheck called without resolved ToolPolicy");
            ctx.response().setStatusCode(403).end();
            return;
        }

        Cookie sessionCookie = ctx.request().getCookie("__toolhub_session");
        if (sessionCookie == null) {
            log.info("AuthCheck failed: no session cookie");
            ctx.response().setStatusCode(401).end();
            return;
        }

        DecodedJWT jwt;
        try {
            jwt = ToolHubSessionJwtProvider.verifySessionToken(
                    sessionCookie.getValue());
        } catch (Exception e) {
            log.info("AuthCheck failed: invalid or expired session", e);
            ctx.response().setStatusCode(401).end();
            return;
        }

        String role = jwt.getClaim("role").asString();
        String email = jwt.getClaim("email").asString();

        // 1️⃣ Optional role enforcement
        if (policy.role != null) {
            if (role == null || !policy.role.equalsIgnoreCase(role)) {
                log.error(
                        "AuthCheck forbidden: role mismatch [required={}, actual={}]",
                        policy.role,
                        role);
                ctx.response().setStatusCode(403).end();
                return;
            }
        }

        // 2️⃣ Email allowlist enforcement (NEW)
        if (!policy.isEmailAllowed(email)) {
            log.warn(
                    "AuthCheck forbidden: email not allowed [email={}, host={}]",
                    email,
                    ctx.request().host());
            ctx.response().setStatusCode(403).end();
            return;
        }

        // ✅ Auth OK
        ctx.response().setStatusCode(200).end();
    }
}
