package com.toolhub.util;

import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.toolhub.config.AppConfig;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class RedirectUtil {

        private static final Logger log = LoggerFactory.getLogger(RedirectUtil.class);

        private static final String LOGIN_BASE_URL = AppConfig.REDIRECT_URL;

        public static void redirectToLogin(RoutingContext ctx) {
                String originalUrl = ctx.request().absoluteURI();

                String redirectUrl = LOGIN_BASE_URL +
                                "?redirect=" +
                                URLEncoder.encode(originalUrl, StandardCharsets.UTF_8);

                log.info(
                                "Redirecting unauthenticated request to login " +
                                                "[method={}, host={}, path={}]",
                                ctx.request().method(),
                                ctx.request().host(),
                                ctx.request().path());

                ctx.response()
                                .setStatusCode(302)
                                .putHeader("Location", redirectUrl)
                                .end();
        }
}
