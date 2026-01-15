package com.toolhub.proxy;

import com.toolhub.config.ToolPolicy;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class ReverseProxyHandler implements Handler<RoutingContext> {

    private static final Logger log = LoggerFactory.getLogger(ReverseProxyHandler.class);

    private final HttpClient httpClient;

    public ReverseProxyHandler(Vertx vertx) {
        this.httpClient = vertx.createHttpClient();
        log.info("ReverseProxyHandler initialized");
    }

    @Override
    public void handle(RoutingContext ctx) {
        ToolPolicy policy = ctx.get("policy");

        String fullTargetUrl = policy.target + ctx.request().uri();

        URI uri = URI.create(fullTargetUrl);

        RequestOptions options = new RequestOptions()
                .setMethod(ctx.request().method())
                .setHost(uri.getHost())
                .setPort(uri.getPort() == -1 ? 80 : uri.getPort())
                .setURI(uri.getRawPath() +
                        (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : ""));

        log.info(
                "Proxying request [method={}, host={}, path={}, target={}]",
                ctx.request().method(),
                ctx.request().host(),
                ctx.request().path(),
                policy.target);

        httpClient.request(options)
                .onFailure(err -> {
                    log.error(
                            "Failed to create proxy request [target={}, method={}, path={}]",
                            policy.target,
                            ctx.request().method(),
                            ctx.request().path(),
                            err);
                    ctx.response().setStatusCode(502).end("Bad Gateway");
                })
                .onSuccess(proxyReq -> {

                    // Copy headers (except Host)
                    ctx.request().headers().forEach(h -> {
                        if (!h.getKey().equalsIgnoreCase("host")) {
                            proxyReq.putHeader(h.getKey(), h.getValue());
                        }
                    });

                    // Stream request body → proxy
                    ctx.request().pipeTo(proxyReq, ar -> {
                        if (ar.failed()) {
                            log.error(
                                    "Failed to stream request body to target [target={}, path={}]",
                                    policy.target,
                                    ctx.request().path(),
                                    ar.cause());
                            ctx.response()
                                    .setStatusCode(500)
                                    .end("Proxy request failed");
                        }
                    });

                    proxyReq.response(ar -> {
                        if (ar.failed()) {
                            log.error(
                                    "Proxy target did not respond [target={}, path={}]",
                                    policy.target,
                                    ctx.request().path(),
                                    ar.cause());
                            ctx.response().setStatusCode(502).end("Bad Gateway");
                            return;
                        }

                        HttpClientResponse proxyRes = ar.result();

                        log.info(
                                "Proxy response received [status={}, target={}, path={}]",
                                proxyRes.statusCode(),
                                policy.target,
                                ctx.request().path());

                        ctx.response().setStatusCode(proxyRes.statusCode());

                        proxyRes.headers().forEach(h -> ctx.response().putHeader(h.getKey(), h.getValue()));

                        proxyRes.pipeTo(ctx.response());
                    });
                });
    }
}
