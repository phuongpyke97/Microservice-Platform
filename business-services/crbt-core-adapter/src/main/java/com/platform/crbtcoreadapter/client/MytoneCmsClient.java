package com.platform.crbtcoreadapter.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

@Component
public class MytoneCmsClient {
    private final RestClient restClient;
    private final String apiKey;

    public MytoneCmsClient(@Value("${mytone.api.base-url}") String baseUrl,
                           @Value("${mytone.api.key}") String apiKey) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    @CircuitBreaker(name = "mytone", fallbackMethod = "assignFallback")
    @Retry(name = "mytone")
    public MytoneCmsResponse assignRingtone(MytoneCmsRequest request) {
        return restClient.post()
            .uri("/ringtones/assign")
            .header("X-API-Key", apiKey)
            .body(request)
            .retrieve()
            .body(MytoneCmsResponse.class);
    }

    @CircuitBreaker(name = "mytone", fallbackMethod = "removeFallback")
    @Retry(name = "mytone")
    public MytoneCmsResponse removeRingtone(String msisdn) {
        return restClient.delete()
            .uri("/ringtones/{msisdn}", msisdn)
            .header("X-API-Key", apiKey)
            .retrieve()
            .body(MytoneCmsResponse.class);
    }

    @SuppressWarnings("unused")
    private MytoneCmsResponse assignFallback(MytoneCmsRequest req, Throwable t) {
        return new MytoneCmsResponse(false, null, "Mytone unavailable: " + t.getMessage());
    }

    @SuppressWarnings("unused")
    private MytoneCmsResponse removeFallback(String msisdn, Throwable t) {
        return new MytoneCmsResponse(false, null, "Mytone unavailable: " + t.getMessage());
    }
}
