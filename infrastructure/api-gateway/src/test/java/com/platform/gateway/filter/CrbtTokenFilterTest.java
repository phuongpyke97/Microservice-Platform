package com.platform.gateway.filter;

import com.platform.gateway.config.SecurityProperties;
import com.platform.gateway.dto.CrbtProvisionResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CrbtTokenFilterTest {

    private static final String SECRET =
            "crbt-test-shared-secret-key-must-be-at-least-256-bits-long-padded-here";
    private static final String MSISDN = "0901234567";

    @Mock private GatewayFilterChain chain;
    @Mock private WebClient.Builder webClientBuilder;
    @Mock private WebClient webClient;
    @Mock private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock private WebClient.RequestBodySpec requestBodySpec;
    @Mock private WebClient.RequestHeadersSpec<?> requestHeadersSpec;
    @Mock private WebClient.ResponseSpec responseSpec;

    private CrbtTokenFilter filter;

    @BeforeEach
    void setUp() {
        SecurityProperties props = new SecurityProperties(
                new SecurityProperties.Jwt("ignored"),
                new SecurityProperties.Crbt(SECRET),
                new SecurityProperties.RateLimit(10, 20));
        when(webClientBuilder.build()).thenReturn(webClient);
        filter = new CrbtTokenFilter(props, webClientBuilder);
    }

    @Test
    @DisplayName("no X-CRBT-Token: passes through, auth-service not called")
    void noToken_passThrough() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/tones").build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
        verifyNoInteractions(webClient);
    }

    @Test
    @DisplayName("blank X-CRBT-Token: passes through")
    void blankToken_passThrough() {
        MockServerWebExchange exchange = exchange(
                MockServerHttpRequest.get("/api/tones").header("X-CRBT-Token", "   ").build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
        verifyNoInteractions(webClient);
    }

    @Test
    @DisplayName("expired token: 401 AUTH_CRBT_TOKEN_EXPIRED")
    void expiredToken_returns401() {
        String token = buildToken(
                Map.of("phone", MSISDN, "status", 1),
                new Date(System.currentTimeMillis() - 10_000));
        MockServerWebExchange exchange = crbtExchange(token);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertResponseContains(exchange, "AUTH_CRBT_TOKEN_EXPIRED");
        verifyNoInteractions(webClient);
    }

    @Test
    @DisplayName("malformed token: 401 AUTH_CRBT_TOKEN_INVALID")
    void malformedToken_returns401() {
        MockServerWebExchange exchange = crbtExchange("not.a.valid.jwt");

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertResponseContains(exchange, "AUTH_CRBT_TOKEN_INVALID");
        verifyNoInteractions(webClient);
    }

    @Test
    @DisplayName("token signed with wrong secret: 401 AUTH_CRBT_TOKEN_INVALID")
    void wrongSecret_returns401() {
        String token = signWith(
                "different-secret-key-that-is-also-at-least-256-bits-padded-here-ok",
                Map.of("phone", MSISDN, "status", 1));
        MockServerWebExchange exchange = crbtExchange(token);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(webClient);
    }

    @Test
    @DisplayName("status=0 (inactive): 403 AUTH_CRBT_SUBSCRIBER_INACTIVE")
    void inactiveSubscriber_returns403() {
        MockServerWebExchange exchange = crbtExchange(validToken(Map.of("phone", MSISDN, "status", 0)));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertResponseContains(exchange, "AUTH_CRBT_SUBSCRIBER_INACTIVE");
        verifyNoInteractions(webClient);
    }

    @Test
    @DisplayName("status missing: 403 AUTH_CRBT_SUBSCRIBER_INACTIVE")
    void missingStatus_returns403() {
        MockServerWebExchange exchange = crbtExchange(validToken(Map.of("phone", MSISDN)));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(webClient);
    }

    @Test
    @DisplayName("no phone and no sub: 400 AUTH_CRBT_TOKEN_MISSING_MSISDN")
    void missingMsisdn_returns400() {
        MockServerWebExchange exchange = crbtExchange(validToken(Map.of("status", 1)));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertResponseContains(exchange, "AUTH_CRBT_TOKEN_MISSING_MSISDN");
    }

    @Test
    @DisplayName("valid token + provision ok: injects X-User-Id, X-MSISDN, X-User-Roles, X-Subscription-Type")
    void validToken_provisionSuccess_injectsHeaders() {
        String token = validToken(Map.of("phone", MSISDN, "status", 1, "loginType", 2));
        stubProvision(Mono.just(new CrbtProvisionResponse(42L, MSISDN, List.of("USER"))));
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(crbtExchange(token), chain)).verifyComplete();

        verify(chain).filter(argThat(ex -> {
            var h = ex.getRequest().getHeaders();
            return "42".equals(h.getFirst("X-User-Id"))
                    && MSISDN.equals(h.getFirst("X-MSISDN"))
                    && "USER".equals(h.getFirst("X-User-Roles"))
                    && "2".equals(h.getFirst("X-Subscription-Type"));
        }));
    }

    @Test
    @DisplayName("valid token: X-CRBT-Token stripped from forwarded request")
    void validToken_crbtHeaderStripped() {
        String token = validToken(Map.of("phone", MSISDN, "status", 1));
        stubProvision(Mono.just(new CrbtProvisionResponse(7L, MSISDN, List.of("USER"))));
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(crbtExchange(token), chain)).verifyComplete();

        verify(chain).filter(argThat(ex ->
                "".equals(ex.getRequest().getHeaders().getFirst("X-CRBT-Token"))));
    }

    @Test
    @DisplayName("phone missing, sub present: uses sub as MSISDN")
    void phoneMissing_fallbackToSub() {
        String token = Jwts.builder()
                .subject(MSISDN)
                .claim("status", 1)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        stubProvision(Mono.just(new CrbtProvisionResponse(99L, MSISDN, List.of("USER"))));
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(crbtExchange(token), chain)).verifyComplete();

        verify(chain).filter(argThat(ex ->
                MSISDN.equals(ex.getRequest().getHeaders().getFirst("X-MSISDN"))));
    }

    @Test
    @DisplayName("auth-service 5xx: 401 AUTH_CRBT_PROVISION_FAILED")
    void provisionHttpError_returns401() {
        String token = validToken(Map.of("phone", MSISDN, "status", 1));
        stubProvision(Mono.error(WebClientResponseException.create(
                503, "Service Unavailable", null, null, null)));
        MockServerWebExchange exchange = crbtExchange(token);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertResponseContains(exchange, "AUTH_CRBT_PROVISION_FAILED");
    }

    @Test
    @DisplayName("unexpected exception from WebClient: 500 AUTH_CRBT_INTERNAL_ERROR")
    void provisionUnexpectedError_returns500() {
        String token = validToken(Map.of("phone", MSISDN, "status", 1));
        stubProvision(Mono.error(new RuntimeException("Connection refused")));
        MockServerWebExchange exchange = crbtExchange(token);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertResponseContains(exchange, "AUTH_CRBT_INTERNAL_ERROR");
    }

    @Test
    @DisplayName("X-Correlation-ID present: passed through to chain")
    void correlationId_present_chainCalled() {
        String token = validToken(Map.of("phone", MSISDN, "status", 1));
        stubProvision(Mono.just(new CrbtProvisionResponse(1L, MSISDN, List.of("USER"))));
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/tones")
                .header("X-CRBT-Token", token)
                .header("X-Correlation-ID", "trace-abc-123")
                .build();

        StepVerifier.create(filter.filter(exchange(request), chain)).verifyComplete();

        verify(chain).filter(any());
    }

    @Test
    @DisplayName("filter order is -150")
    void filterOrder() {
        assertThat(filter.getOrder()).isEqualTo(-150);
    }

    // --- helpers ---

    private MockServerWebExchange exchange(MockServerHttpRequest request) {
        return MockServerWebExchange.from(request);
    }

    private MockServerWebExchange crbtExchange(String token) {
        return exchange(MockServerHttpRequest.get("/api/tones")
                .header("X-CRBT-Token", token)
                .build());
    }

    private String validToken(Map<String, Object> claims) {
        return buildToken(claims, new Date(System.currentTimeMillis() + 60_000));
    }

    private String buildToken(Map<String, Object> claims, Date expiry) {
        var builder = Jwts.builder()
                .expiration(expiry)
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)));
        claims.forEach(builder::claim);
        return builder.compact();
    }

    private String signWith(String secret, Map<String, Object> claims) {
        var builder = Jwts.builder()
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)));
        claims.forEach(builder::claim);
        return builder.compact();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubProvision(Mono<CrbtProvisionResponse> response) {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(CrbtProvisionResponse.class)).thenReturn(response);
    }

    private void assertResponseContains(MockServerWebExchange exchange, String errorCode) {
        var body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains(errorCode);
    }
}
