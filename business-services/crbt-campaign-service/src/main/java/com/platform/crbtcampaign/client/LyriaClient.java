package com.platform.crbtcampaign.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class LyriaClient {
    private final RestClient restClient;
    private final String apiKey;

    public LyriaClient(@Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta}") String url,
                       @Value("${lyria.api-key:changeme}") String apiKey) {
        this.restClient = RestClient.builder().baseUrl(url).build();
        this.apiKey = apiKey;
    }

    @CircuitBreaker(name = "lyria")
    public byte[] generateMusic(String prompt) {
        if (apiKey == null || apiKey.isBlank() || "changeme".equals(apiKey)) {
            throw new IllegalArgumentException("GEMINI_API_KEY is not configured or is empty");
        }

        // Google Gemini Lyria 3 API endpoint
        return restClient.post()
            .uri(uriBuilder -> uriBuilder
                .path("/models/lyria-3-clip-preview:generateContent")
                .queryParam("key", apiKey)
                .build())
            .body(Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))))
            .retrieve()
            .body(byte[].class);
    }
}
