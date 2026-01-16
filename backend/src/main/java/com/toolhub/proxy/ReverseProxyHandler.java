package com.toolhub.proxy;

import com.toolhub.config.ToolPolicy;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class ReverseProxyHandler implements Handler<RoutingContext> {

    private static final Logger log = LoggerFactory.getLogger(ReverseProxyHandler.class);

    private final WebClient webClient;
    private final HttpClient httpClient;

    public ReverseProxyHandler(WebClient webClient, HttpClient httpClient) {
        this.webClient = webClient;
        this.httpClient = httpClient;
        log.info("ReverseProxyHandler initialized (HTTP + SSE)");
    }

    @Override
    public void handle(RoutingContext ctx) {
        ToolPolicy policy = ctx.get("policy");

        if (isSse(ctx)) {
            proxySse(ctx, policy);
        } else {
            proxyHttp(ctx, policy);
        }
    }

    /* ===================== SSE ===================== */

    private void proxySse(RoutingContext ctx, ToolPolicy policy) {
        URI target = URI.create(policy.target);

        log.info(
                "Proxying SSE [host={}, path={}, target={}]",
                ctx.request().host(),
                ctx.request().path(),
                policy.target);

        httpClient.request(
                ctx.request().method(),
                target.getPort() == -1 ? 80 : target.getPort(),
                target.getHost(),
                ctx.request().uri())
                .onFailure(err -> {
                    log.error("SSE upstream connection failed", err);
                    ctx.response().setStatusCode(502).end();
                })
                .onSuccess(proxyReq -> {

                    // Copy headers
                    ctx.request().headers().forEach(h -> {
                        if (!h.getKey().equalsIgnoreCase("host")) {
                            proxyReq.putHeader(h.getKey(), h.getValue());
                        }
                    });

                    proxyReq.send(ar -> {
                        if (ar.failed()) {
                            ctx.response().setStatusCode(502).end();
                            return;
                        }

                        HttpClientResponse upstream = ar.result();
                        HttpServerResponse downstream = ctx.response();

                        // ⭐ REQUIRED FOR SSE
                        downstream.setChunked(true);
                        downstream.setStatusCode(upstream.statusCode());

                        upstream.headers().forEach(h -> downstream.putHeader(h.getKey(), h.getValue()));

                        // Stream chunks forever
                        upstream.handler(downstream::write);

                        upstream.endHandler(v -> downstream.end());
                        upstream.exceptionHandler(err -> {
                            log.error("SSE stream error", err);
                            downstream.end();
                        });
                    });
                });
    }

    /* ===================== HTTP ===================== */

    private void proxyHttp(RoutingContext ctx, ToolPolicy policy) {
        String targetUrl = policy.target + ctx.request().uri();
        HttpMethod method = ctx.request().method();

        log.info(
                "Proxying HTTP [method={}, host={}, path={}, target={}]",
                method,
                ctx.request().host(),
                ctx.request().path(),
                targetUrl);

        HttpRequest<Buffer> proxyReq = webClient.requestAbs(method, targetUrl);

        ctx.request().headers().forEach(h -> {
            String name = h.getKey();
            if (name.equalsIgnoreCase("host"))
                return;
            if (name.equalsIgnoreCase("authorization"))
                return;
            if (name.equalsIgnoreCase("cookie"))
                return;
            proxyReq.putHeader(name, h.getValue());
        });

        Buffer body = ctx.body().buffer();

        proxyReq
                .timeout(30_000)
                .sendBuffer(body, ar -> {
                    if (ar.failed()) {
                        log.error("HTTP proxy failed", ar.cause());
                        ctx.response().setStatusCode(502).end("Bad Gateway");
                        return;
                    }

                    HttpResponse<Buffer> proxyRes = ar.result();
                    ctx.response().setStatusCode(proxyRes.statusCode());
                    proxyRes.headers()
                            .forEach(h -> ctx.response().putHeader(h.getKey(), h.getValue()));

                    ctx.response().end(proxyRes.body());
                });
    }

    /* ===================== Helpers ===================== */

    private boolean isSse(RoutingContext ctx) {
        String accept = ctx.request().getHeader("Accept");
        return accept != null && accept.contains("text/event-stream");
    }
}
