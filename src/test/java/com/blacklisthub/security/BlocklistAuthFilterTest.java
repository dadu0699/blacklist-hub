package com.blacklisthub.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class BlocklistAuthFilterTest {

    private static final String TOKEN = "s3cr3t-token";

    /** A chain that "handles" the request without setting a status code. */
    private static final WebFilterChain PASS_THROUGH = exchange -> Mono.empty();

    @Test
    void allowsBlocklistRequestWithValidToken() {
        BlocklistAuthFilter filter = new BlocklistAuthFilter(TOKEN);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/blacklist/ips.txt")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN));

        StepVerifier.create(filter.filter(exchange, PASS_THROUGH)).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void rejectsBlocklistRequestWithWrongToken() {
        BlocklistAuthFilter filter = new BlocklistAuthFilter(TOKEN);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/blacklist/ips.txt")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer wrong"));

        StepVerifier.create(filter.filter(exchange, PASS_THROUGH)).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsBlocklistRequestWithoutAuthorizationHeader() {
        BlocklistAuthFilter filter = new BlocklistAuthFilter(TOKEN);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/blacklist/hashes.txt"));

        StepVerifier.create(filter.filter(exchange, PASS_THROUGH)).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsEveryBlocklistRequestWhenTokenNotConfigured() {
        BlocklistAuthFilter filter = new BlocklistAuthFilter("");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/blacklist/ips.txt")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer anything"));

        StepVerifier.create(filter.filter(exchange, PASS_THROUGH)).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void ignoresNonBlocklistPathsEvenWithoutToken() {
        BlocklistAuthFilter filter = new BlocklistAuthFilter("");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health"));

        StepVerifier.create(filter.filter(exchange, PASS_THROUGH)).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }
}
