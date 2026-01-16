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

        // Determine the correct port and scheme
        int port = target.getPort();
        if (port == -1) {
            port = "https".equalsIgnoreCase(target.getScheme()) ? 443 : 80;
        }

        // Use HTTPS if the target URL specifies it
        boolean ssl = "https".equalsIgnoreCase(target.getScheme());

        HttpClientRequest upstreamRequest = httpClient.request(
                new RequestOptions()
                        .setMethod(ctx.request().method())
                        .setPort(port)
                        .setHost(target.getHost())
                        .setURI(ctx.request().uri())
                        .setSsl(ssl)
                        .setTimeout(0) // No timeout for SSE streams
        ).result();

        if (upstreamRequest == null) {
            log.error("Failed to create upstream SSE request");
            ctx.response().setStatusCode(502).end();
            return;
        }

        // Copy headers from client to upstream
        ctx.request().headers().forEach(h -> {
            String headerName = h.getKey().toLowerCase();
            // Skip headers that shouldn't be forwarded
            if (!headerName.equals("host") &&
                    !headerName.equals("connection") &&
                    !headerName.equals("keep-alive") &&
                    !headerName.equals("transfer-encoding")) {
                upstreamRequest.putHeader(h.getKey(), h.getValue());
            }
        });

        // Ensure proper SSE headers on upstream request
        upstreamRequest.putHeader("Accept", "text/event-stream");
        upstreamRequest.putHeader("Cache-Control", "no-cache");
        upstreamRequest.putHeader("Connection", "keep-alive");

        // Handle connection failures
        upstreamRequest.exceptionHandler(err -> {
            log.error("SSE upstream connection failed", err);
            if (!ctx.response().ended()) {
                ctx.response().setStatusCode(502).end();
            }
        });

        // Send the request and handle the response
        upstreamRequest.send(ctx.body().buffer())
                .onFailure(err -> {
                    log.error("SSE upstream send failed", err);
                    if (!ctx.response().ended()) {
                        ctx.response().setStatusCode(502).end();
                    }
                })
                .onSuccess(upstreamResponse -> {
                    HttpServerResponse downstream = ctx.response();

                    // Set up SSE response headers BEFORE any data is written
                    downstream.setStatusCode(upstreamResponse.statusCode());
                    downstream.setChunked(true);

                    // Copy response headers from upstream
                    upstreamResponse.headers().forEach(h -> {
                        String headerName = h.getKey().toLowerCase();
                        // Skip headers that Vert.x manages or that conflict with chunked encoding
                        if (!headerName.equals("transfer-encoding") &&
                                !headerName.equals("content-length") &&
                                !headerName.equals("connection")) {
                            downstream.putHeader(h.getKey(), h.getValue());
                        }
                    });

                    // Ensure critical SSE headers are set
                    downstream.putHeader("Content-Type", "text/event-stream");
                    downstream.putHeader("Cache-Control", "no-cache");
                    downstream.putHeader("Connection", "keep-alive");
                    downstream.putHeader("X-Accel-Buffering", "no"); // Disable nginx buffering if behind nginx

                    // Stream data from upstream to downstream
                    upstreamResponse.handler(chunk -> {
                        if (!downstream.ended()) {
                            downstream.write(chunk);
                        }
                    });

                    // Handle upstream completion
                    upstreamResponse.endHandler(v -> {
                        log.info("SSE upstream ended normally");
                        if (!downstream.ended()) {
                            downstream.end();
                        }
                    });

                    // Handle upstream errors
                    upstreamResponse.exceptionHandler(err -> {
                        log.error("SSE stream error from upstream", err);
                        if (!downstream.ended()) {
                            downstream.end();
                        }
                    });

                    // Handle downstream close (client disconnected)
                    downstream.closeHandler(v -> {
                        log.info("SSE downstream connection closed by client");
                        upstreamResponse.request().reset();
                    });

                    // Handle downstream errors
                    downstream.exceptionHandler(err -> {
                        log.error("SSE downstream error", err);
                        upstreamResponse.request().reset();
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
            String name = h.getKey().toLowerCase();
            if (name.equals("host"))
                return;
            if (name.equals("authorization"))
                return;
            if (name.equals("cookie"))
                return;
            // Also skip connection-specific headers
            if (name.equals("connection"))
                return;
            if (name.equals("keep-alive"))
                return;
            if (name.equals("transfer-encoding"))
                return;
            proxyReq.putHeader(h.getKey(), h.getValue());
        });

        Buffer body = ctx.body().buffer();

        proxyReq
                .timeout(30_000)
                .sendBuffer(body, ar -> {
                    if (ar.failed()) {
                        log.error("HTTP proxy failed", ar.cause());
                        if (!ctx.response().ended()) {
                            ctx.response().setStatusCode(502).end("Bad Gateway");
                        }
                        return;
                    }

                    HttpResponse<Buffer> proxyRes = ar.result();
                    ctx.response().setStatusCode(proxyRes.statusCode());
                    proxyRes.headers().forEach(h -> {
                        String headerName = h.getKey().toLowerCase();
                        if (!headerName.equals("transfer-encoding") &&
                                !headerName.equals("connection")) {
                            ctx.response().putHeader(h.getKey(), h.getValue());
                        }
                    });

                    ctx.response().end(proxyRes.body());
                });
    }

    /* ===================== Helpers ===================== */

    private boolean isSse(RoutingContext ctx) {
        String accept = ctx.request().getHeader("Accept");
        return accept != null && accept.contains("text/event-stream");
    }
}