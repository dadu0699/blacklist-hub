package com.blacklisthub.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Guards the public blocklist endpoints ({@code /blacklist/**}) with a static
 * bearer token supplied via {@code app.api-token} (env {@code APP_API_TOKEN}).
 *
 * <p>
 * Fail-closed by design: if no token is configured, every protected request is
 * rejected with {@code 401}. This prevents the endpoints from silently
 * reverting to an unauthenticated state if the variable is ever missing.
 *
 * <p>
 * Only {@code /blacklist/**} is filtered; {@code /actuator/**} is intentionally
 * left untouched so that liveness/readiness probes keep working.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BlocklistAuthFilter implements WebFilter {

    private static final String PROTECTED_PREFIX = "/blacklist";
    private static final String BEARER_PREFIX = "Bearer ";

    private final boolean enabled;
    private final byte[] expectedToken;

    public BlocklistAuthFilter(@Value("${app.api-token:}") String apiToken) {
        this.enabled = apiToken != null && !apiToken.isBlank();
        this.expectedToken = enabled ? apiToken.getBytes(StandardCharsets.UTF_8) : new byte[0];
        if (!enabled) {
            log.warn("app.api-token is not set: all {}/** requests will be rejected with 401. "
                    + "Set APP_API_TOKEN to allow blocklist consumers.", PROTECTED_PREFIX);
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        final ServerHttpRequest request = exchange.getRequest();
        final String path = request.getPath().value();

        if (!path.startsWith(PROTECTED_PREFIX)) {
            return chain.filter(exchange);
        }

        if (isAuthorized(request)) {
            return chain.filter(exchange);
        }

        log.warn("Rejected unauthorized request to {}", path);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private boolean isAuthorized(ServerHttpRequest request) {
        if (!enabled) {
            return false;
        }
        final String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return false;
        }
        final byte[] provided = header.substring(BEARER_PREFIX.length()).getBytes(StandardCharsets.UTF_8);
        // Constant-time comparison to avoid leaking the token via timing.
        return MessageDigest.isEqual(expectedToken, provided);
    }
}
