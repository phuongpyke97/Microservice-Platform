package com.platform.crbtcampaign.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class LyriaClient {
    private static final Logger log = LoggerFactory.getLogger(LyriaClient.class);
    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;

    public LyriaClient(@Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta}") String url,
                       @Value("${lyria.api-key:changeme}") String apiKey,
                       @Value("${lyria.model:lyria-3-clip-preview}") String model,
                       @Value("${lyria.timeout.connect-ms:5000}") int connectTimeout,
                       @Value("${lyria.timeout.read-ms:90000}") int readTimeout,
                       @Value("${lyria.pool.max-total:20}") int poolMaxTotal,
                       @Value("${lyria.pool.max-per-route:20}") int poolMaxPerRoute,
                       @Value("${lyria.pool.conn-ttl-sec:300}") int poolConnTtlSec,
                       ObjectMapper objectMapper) {

        // --- Connection Pool (Apache HttpClient 5) ---
        ConnectionConfig connConfig = ConnectionConfig.custom()
                .setTimeToLive(TimeValue.of(poolConnTtlSec, TimeUnit.SECONDS))
                .build();

        PoolingHttpClientConnectionManager connManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(poolMaxTotal)
                .setMaxConnPerRoute(poolMaxPerRoute)
                .setDefaultConnectionConfig(connConfig)
                .build();

        HttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connManager)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.of(connectTimeout, TimeUnit.MILLISECONDS))
                        .setResponseTimeout(Timeout.of(readTimeout, TimeUnit.MILLISECONDS))
                        .build())
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.of(60, TimeUnit.SECONDS))
                .build();

        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        // NOTE: timeouts are fully managed via RequestConfig above;
        //       do NOT call requestFactory.setConnectTimeout() here — it has no effect
        //       when a custom HttpClient with its own RequestConfig is provided.

        this.restClient = RestClient.builder()
            .baseUrl(url)
            .requestFactory(requestFactory)
            .requestInterceptor(new MaskedLoggingInterceptor())
            .build();
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;
        log.info("[LYRIA-CLIENT-INIT] Initialized with model={}, apiKey={}, connectTimeout={}ms, readTimeout={}ms, pool(maxTotal={}, maxPerRoute={}, ttl={}s)",
                 model, maskKey(apiKey), connectTimeout, readTimeout, poolMaxTotal, poolMaxPerRoute, poolConnTtlSec);
    }

    @CircuitBreaker(name = "lyria")
    public byte[] generateMusic(String prompt) {
        // Default path: deterministic output (seed=0 omits the seed override).
        return generateMusic(prompt, 0L);
    }

    /**
     * Generate music with a per-request {@code seed} so repeated calls with the
     * same prompt produce distinct audio. A non-positive seed leaves seed unset
     * (model default behaviour).
     */
    @CircuitBreaker(name = "lyria")
    public byte[] generateMusic(String prompt, long seed) {
        if (apiKey == null || apiKey.isBlank() || "changeme".equals(apiKey)) {
            throw new IllegalArgumentException("GEMINI_API_KEY is not configured or is empty");
        }

        log.info("[LYRIA-API-CALL] Sending prompt to Gemini API... Prompt length: {}, seed: {}",
                 prompt != null ? prompt.length() : 0, seed);

        // generationConfig steers diversity: temperature widens sampling, seed makes
        // each regeneration distinct while keeping the same genre/mood/instrument prompt.
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 1.0);
        if (seed > 0) {
            generationConfig.put("seed", seed);
        }
        Map<String, Object> requestBody = Map.of(
            "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
            "generationConfig", generationConfig);

        // Google Gemini Lyria 3 API endpoint
        // Read as byte[] to handle both application/json and application/octet-stream responses
        String jsonResponse;
        try {
            byte[] rawBytes = restClient.post()
                .uri(uriBuilder -> uriBuilder
                    .path("/models/" + model + ":generateContent")
                    .queryParam("key", apiKey)
                    .build())
                .body(requestBody)
                .retrieve()
                .body(byte[].class);
            jsonResponse = rawBytes != null ? new String(rawBytes, java.nio.charset.StandardCharsets.UTF_8) : null;
            log.info("[LYRIA-API-RESPONSE] Received response successfully, JSON length={}", jsonResponse != null ? jsonResponse.length() : 0);
        } catch (org.springframework.web.client.RestClientException e) {
            log.error("[LYRIA-API-ERROR] RestClientException when calling Gemini API: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("[LYRIA-API-ERROR] Unexpected exception when calling Gemini API: {}", e.getMessage(), e);
            // Wrap any non-runtime checked exception so callers don't need throws declaration
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException("Unexpected error calling Gemini Lyria API", e);
        }

        if (jsonResponse == null || jsonResponse.isBlank()) {
            log.error("[LYRIA-API-ERROR] Received null or empty response body from Gemini API");
            throw new IllegalStateException("Empty response body from Gemini Lyria API");
        }

        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);

            JsonNode usageMetadata = rootNode.path("usageMetadata");
            if (!usageMetadata.isMissingNode()) {
                int promptTokens = usageMetadata.path("promptTokenCount").asInt();
                int candidateTokens = usageMetadata.path("candidatesTokenCount").asInt();
                int totalTokens = usageMetadata.path("totalTokenCount").asInt();

                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest request = attributes.getRequest();
                    request.setAttribute("lyria_token_usage", Map.of(
                        "prompt_tokens", promptTokens,
                        "candidate_tokens", candidateTokens,
                        "total_tokens", totalTokens
                    ));
                }
                log.info("[LYRIA-TOKEN-USAGE] input_tokens={}, output_tokens={}, total_tokens={}", 
                         promptTokens, candidateTokens, totalTokens);
            }

            JsonNode candidates = rootNode.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode firstCandidate = candidates.get(0);
                String finishReason = firstCandidate.path("finishReason").asText();
                String finishMessage = firstCandidate.path("finishMessage").asText();
                if (finishReason != null && !finishReason.isEmpty() && !"STOP".equalsIgnoreCase(finishReason)) {
                    log.warn("[LYRIA-API-FILTERED] Gemini Lyria content filtered (reason={}): {}", finishReason, finishMessage);
                    throw new LyriaContentFilteredException(finishMessage != null && !finishMessage.isEmpty() ? finishMessage : "Content filtered with reason: " + finishReason);
                }

                JsonNode parts = firstCandidate.path("content").path("parts");
                if (parts.isArray()) {
                    for (JsonNode part : parts) {
                        JsonNode inlineData = part.path("inlineData");
                        if (!inlineData.isMissingNode()) {
                            String base64Data = inlineData.path("data").asText();
                            if (base64Data != null && !base64Data.isBlank()) {
                                byte[] decoded = Base64.getDecoder().decode(base64Data);
                                log.info("[LYRIA-API-DECODE] Decoded audio successfully, size={} bytes", decoded.length);
                                return decoded;
                            }
                        }
                    }
                }
            }
            log.error("[LYRIA-API-ERROR] Failed to locate audio data (inlineData) in JSON response: {}", jsonResponse);
            throw new IllegalStateException("Failed to find inlineData/audio in Gemini Lyria API response");
        } catch (LyriaContentFilteredException e) {
            // Re-throw directly
            throw e;
        } catch (IllegalStateException e) {
            // Re-throw directly — do not double-wrap
            throw e;
        } catch (Exception e) {
            log.error("[LYRIA-API-PARSE-ERROR] Failed to parse Gemini response: {}", e.getMessage(), e);
            throw new RuntimeException("Error processing Gemini Lyria response", e);
        }
    }

    /**
     * Interceptor that logs the outgoing request URL with sensitive query params (e.g. "key") masked as ***.
     * The actual HTTP request is forwarded unchanged — only the log output is sanitized.
     */
    private static class MaskedLoggingInterceptor implements ClientHttpRequestInterceptor {

        // Reuse the outer class logger — ensures it respects the same log-level config
        private static final Logger ilog = LoggerFactory.getLogger(LyriaClient.class);

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                            ClientHttpRequestExecution execution) throws IOException {
            String maskedUri = maskSensitiveParams(request.getURI().toString());
            ilog.debug("[LYRIA-HTTP-OUT] {} {}", request.getMethod(), maskedUri);
            return execution.execute(request, body);
        }

        /**
         * Replaces the value of the "key" query parameter with *** in the URI string.
         * Example: ?key=AIzaSyABC123 -> ?key=***
         */
        private String maskSensitiveParams(String uri) {
            if (uri == null) return "";
            return uri.replaceAll("(?i)((?:^|[?&])key=)[^&]*", "$1***");
        }
    }

    /** Mask API key for safe inline logging (first 4 chars + ***). */
    private String maskKey(String key) {
        if (key == null || key.length() <= 4) return "***";
        return key.substring(0, 4) + "***";
    }

    public static class LyriaContentFilteredException extends RuntimeException {
        public LyriaContentFilteredException(String message) {
            super(message);
        }
    }
}
