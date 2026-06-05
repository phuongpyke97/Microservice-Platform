package com.platform.crbtcampaign.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class LyriaClient {
    private static final Logger log = LoggerFactory.getLogger(LyriaClient.class);
    private final RestClient restClient;
    private final String apiKey;
    private final ObjectMapper objectMapper;

    public LyriaClient(@Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta}") String url,
                       @Value("${lyria.api-key:changeme}") String apiKey,
                       ObjectMapper objectMapper) {
        org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(20000);
        this.restClient = RestClient.builder()
            .baseUrl(url)
            .requestFactory(requestFactory)
            .build();
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
    }

    @CircuitBreaker(name = "lyria")
    public byte[] generateMusic(String prompt) {
        if (apiKey == null || apiKey.isBlank() || "changeme".equals(apiKey)) {
            throw new IllegalArgumentException("GEMINI_API_KEY is not configured or is empty");
        }

        log.info("[LYRIA-API-CALL] Sending prompt to Gemini API... Prompt length: {}", prompt != null ? prompt.length() : 0);

        // Google Gemini Lyria 3 API endpoint
        String jsonResponse;
        try {
            jsonResponse = restClient.post()
                .uri(uriBuilder -> uriBuilder
                    .path("/models/lyria-3-clip-preview:generateContent")
                    .queryParam("key", apiKey)
                    .build())
                .body(Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))))
                .retrieve()
                .body(String.class);
            log.info("[LYRIA-API-RESPONSE] Received response successfully, JSON length={}", jsonResponse != null ? jsonResponse.length() : 0);
        } catch (org.springframework.web.client.RestClientException e) {
            log.error("[LYRIA-API-ERROR] RestClientException when calling Gemini API: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("[LYRIA-API-ERROR] Unexpected exception when calling Gemini API: {}", e.getMessage(), e);
            throw e;
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
                JsonNode parts = candidates.get(0).path("content").path("parts");
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
        } catch (Exception e) {
            log.error("[LYRIA-API-PARSE-ERROR] Failed to parse Gemini response: {}", e.getMessage(), e);
            throw new RuntimeException("Error processing Gemini Lyria response", e);
        }
    }
}
