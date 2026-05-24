package com.platform.audiogeneration.client;

import com.platform.common.core.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.Map;

@FeignClient(name = "ai-media-worker", url = "http://${AI_WORKER_HOST:localhost}:8765", fallback = AiMediaWorkerClientFallback.class)
public interface AiMediaWorkerClient {
    // Note: In real world, we'd use gRPC for high perf, but for this sprint we use the FastAPI HTTP wrapper
    @PostMapping("/generate-tts")
    byte[] generateTts(@RequestBody Map<String, String> request);
}

class AiMediaWorkerClientFallback implements AiMediaWorkerClient {
    @Override
    public byte[] generateTts(Map<String, String> request) {
        throw new RuntimeException("AI Media Worker unavailable");
    }
}
