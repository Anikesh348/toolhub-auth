package com.toolhub.proxy;

import com.toolhub.config.ToolPolicy;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        String uri = ctx.request().uri();

        log.info(
                "Proxying SSE [host={}, path={}, target={}]",
                ctx.request().host(),
                ctx.request().path(),
                policy.target);

        httpClient
                .request(
                        ctx.request().method(),
                        extractPort(policy.target),
                        extractHost(policy.target),
                        uri)
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

                    // VERY IMPORTANT: no buffering, no timeout
                    proxyReq.send(ar -> {
                        if (ar.failed()) {
                            ctx.response().setStatusCode(502).end();
                            return;
                        }

                        ar.result().pipeTo(ctx.response());
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

        // Copy headers
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
                        log.error(
                                "HTTP proxy failed [target={}, path={}]",
                                policy.target,
                                ctx.request().path(),
                                ar.cause());
                        ctx.response().setStatusCode(502).end("Bad Gateway");
                        return;
                    }

                    HttpResponse<Buffer> proxyRes = ar.result();
                    ctx.response().setStatusCode(proxyRes.statusCode());
                    proxyRes.headers()
                            .forEach(h -> ctx.response().putHeader(h.getKey(), h.getValue()));

                    if (proxyRes.body() != null) {
                        ctx.response().end(proxyRes.body());
                    } else {
                        ctx.response().end();
                    }
                });
    }

    /* ===================== Helpers ===================== */

    private boolean isSse(RoutingContext ctx) {
        String accept = ctx.request().getHeader("Accept");
        return accept != null && accept.contains("text/event-stream");
    }

    private String extractHost(String target) {
        return target.replace("http://", "")
                .replace("https://", "")
                .split(":")[0];
    }

    private int extractPort(String target) {
        if (target.contains(":")) {
            return Integer.parseInt(
                    target.substring(target.lastIndexOf(":") + 1));
        }
        return 80;
    }
}
