package com.toolhub.proxy;

import com.toolhub.config.ToolPolicy;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
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

    public ReverseProxyHandler(WebClient webClient) {
        this.webClient = webClient;
        log.info("ReverseProxyHandler initialized (WebClient)");
    }

    @Override
    public void handle(RoutingContext ctx) {
        ToolPolicy policy = ctx.get("policy");
        String targetUrl = policy.target + ctx.request().uri();
        HttpMethod method = ctx.request().method();

        log.info(
                "Proxying request [method={}, host={}, path={}, target={}]",
                method,
                ctx.request().host(),
                ctx.request().path(),
                targetUrl);

        HttpRequest<Buffer> proxyReq = webClient.requestAbs(method, targetUrl);

        // Copy headers except sensitive ones
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

        // Get the request body
        Buffer body = ctx.body().buffer();

        proxyReq
                .timeout(30_000)
                .sendBuffer(body, ar -> {
                    if (ar.failed()) {
                        log.error(
                                "Proxy request failed [target={}, path={}]",
                                policy.target,
                                ctx.request().path(),
                                ar.cause());
                        ctx.response()
                                .setStatusCode(502)
                                .end("Bad Gateway");
                        return;
                    }

                    HttpResponse<Buffer> proxyRes = ar.result();
                    ctx.response().setStatusCode(proxyRes.statusCode());
                    proxyRes.headers().forEach(h -> ctx.response().putHeader(h.getKey(), h.getValue()));

                    if (proxyRes.body() != null) {
                        ctx.response().end(proxyRes.body());
                    } else {
                        ctx.response().end();
                    }
                });
    }
}