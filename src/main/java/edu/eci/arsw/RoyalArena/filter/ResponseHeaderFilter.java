package com.escuela.techcup.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * Global filter that removes any existing CORS response headers coming from
 * downstream services so that the gateway can set permissive CORS headers.
 * Allows ALL origins with credentials — for development only.
 */
@Component
public class ResponseHeaderFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ResponseHeaderFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var response = exchange.getResponse();
        String origin = exchange.getRequest().getHeaders().getFirst(HttpHeaders.ORIGIN);

        response.beforeCommit(() -> {
            try {
                var headers = response.getHeaders();
                headers.remove(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
                headers.remove(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS);

                // Allow any origin — fully permissive CORS
                String allowedOrigin = (origin != null && !origin.isBlank()) ? origin : "*";
                headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, allowedOrigin);
                headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
                headers.add(HttpHeaders.VARY, HttpHeaders.ORIGIN);
            } catch (Exception e) {
                log.warn("Failed to normalize response CORS headers", e);
            }
            return Mono.<Void>empty();
        });

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
