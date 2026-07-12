package edu.eci.arsw.RoyalArena.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthFilter implements GatewayFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.internal.secret:techcup-internal-secret-dev}")
    private String internalSecret;

    @Value("${app.security.public-paths:}")
    private List<String> publicPaths;

    private volatile Set<String> publicPathSet;

    private Set<String> getPublicPathSet() {
        if (publicPathSet == null) {
            synchronized (this) {
                if (publicPathSet == null) {
                    if (publicPaths == null) {
                        publicPaths = Collections.emptyList();
                    }
                    publicPathSet = ConcurrentHashMap.newKeySet();
                    publicPathSet.addAll(publicPaths);
                }
            }
        }
        return publicPathSet;
    }

    private boolean isPublicPath(HttpMethod method, String path) {
        if (method == HttpMethod.OPTIONS) {
            return true;
        }
        String key = method.name() + " " + path;
        return getPublicPathSet().contains(key);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        HttpMethod method = request.getMethod();

        if (isPublicPath(method, path)) {
            log.debug("Public path {} {}, skipping auth", method, path);
            return chain.filter(exchange);
        }

        // Allow internal service-to-service calls through the gateway
        String internalSecretHeader = request.getHeaders().getFirst("X-Internal-Secret");
        if (internalSecret != null && !internalSecret.isBlank()
                && internalSecret.equals(internalSecretHeader)) {
            log.debug("Internal call with valid X-Internal-Secret on path {} {}, skipping auth", method, path);
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Auth failed: no Bearer token on path {} {}", method, path);
            return onError(exchange, "TOKEN_NO_ENCONTRADO", "Token no encontrado", HttpStatus.UNAUTHORIZED);
        }

        String token = authHeader.substring(7);

        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("Auth failed: expired token on path {} {}", method, path);
            return onError(exchange, "TOKEN_EXPIRADO", "Token expirado", HttpStatus.UNAUTHORIZED);
        } catch (JwtException e) {
            log.warn("Auth failed: invalid token on path {} {} — {}", method, path, e.getMessage());
            return onError(exchange, "TOKEN_INVALIDO", "Token inválido", HttpStatus.UNAUTHORIZED);
        }

        String userId = claims.get("userId", String.class);
        if (userId == null) {
            userId = claims.getSubject();
        }
        if (userId == null || userId.isBlank()) {
            log.warn("Auth failed: token without user id on path {} {}", method, path);
            return onError(exchange, "TOKEN_INVALIDO", "Token invalido", HttpStatus.UNAUTHORIZED);
        }

        String role = claims.get("role", String.class);
        String email = claims.get("email", String.class);

        ServerHttpRequest.Builder requestBuilder = request.mutate();
        addHeaderIfPresent(requestBuilder, "X-User-Id", userId);
        addHeaderIfPresent(requestBuilder, "X-User-Role", role);
        addHeaderIfPresent(requestBuilder, "X-User-Email", email);
        ServerHttpRequest mutatedRequest = requestBuilder.build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private Mono<Void> onError(ServerWebExchange exchange, String errorCode, String message, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        addCorsHeaders(exchange, response);

        String body = String.format("{\"error\":\"%s\",\"message\":\"%s\"}", errorCode, message);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private void addCorsHeaders(ServerWebExchange exchange, ServerHttpResponse response) {
        String origin = exchange.getRequest().getHeaders().getFirst(HttpHeaders.ORIGIN);
        String allowedOrigin = (origin != null && !origin.isBlank()) ? origin : "*";
        response.getHeaders().set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, allowedOrigin);
        response.getHeaders().set(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
    }

    private void addHeaderIfPresent(ServerHttpRequest.Builder requestBuilder, String name, String value) {
        if (value != null && !value.isBlank()) {
            requestBuilder.header(name, value);
        }
    }
}
