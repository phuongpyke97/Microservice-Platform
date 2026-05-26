package com.platform.crbtcampaign.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class LyriaClient {
    private final RestClient restClient;
    private final String apiKey;
    private final ObjectMapper objectMapper;

    public LyriaClient(@Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta}") String url,
                       @Value("${lyria.api-key:changeme}") String apiKey,
                       ObjectMapper objectMapper) {
        this.restClient = RestClient.builder().baseUrl(url).build();
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
    }

    @CircuitBreaker(name = "lyria")
    public byte[] generateMusic(String prompt) {
        if (apiKey == null || apiKey.isBlank() || "changeme".equals(apiKey)) {
            throw new IllegalArgumentException("GEMINI_API_KEY is not configured or is empty");
        }

        // Google Gemini Lyria 3 API endpoint
        String jsonResponse = restClient.post()
            .uri(uriBuilder -> uriBuilder
                .path("/models/lyria-3-clip-preview:generateContent")
                .queryParam("key", apiKey)
                .build())
            .body(Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))))
            .retrieve()
            .body(String.class);

        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            JsonNode candidates = rootNode.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray()) {
                    for (JsonNode part : parts) {
                        JsonNode inlineData = part.path("inlineData");
                        if (!inlineData.isMissingNode()) {
                            String base64Data = inlineData.path("data").asText();
                            if (base64Data != null && !base64Data.isBlank()) {
                                return Base64.getDecoder().decode(base64Data);
                            }
                        }
                    }
                }
            }
            throw new IllegalStateException("Failed to find inlineData/audio in Gemini Lyria API response: " + jsonResponse);
        } catch (Exception e) {
            throw new RuntimeException("Error processing Gemini Lyria response", e);
        }
    }
}
