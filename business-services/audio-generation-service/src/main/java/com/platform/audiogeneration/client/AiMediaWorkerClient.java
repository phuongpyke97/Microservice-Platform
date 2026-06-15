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

    @PostMapping(value = "/detect-chorus", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    Map<String, Object> detectChorus(@org.springframework.web.bind.annotation.RequestPart("file") org.springframework.web.multipart.MultipartFile file);

    @PostMapping(value = "/separate-audio", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    Map<String, Object> separateAudio(
        @org.springframework.web.bind.annotation.RequestPart("file") org.springframework.web.multipart.MultipartFile file,
        @org.springframework.web.bind.annotation.RequestParam("exclude_audio") boolean excludeAudio
    );

    @PostMapping(value = "/mix-audio", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    byte[] mixAudio(
        @org.springframework.web.bind.annotation.RequestPart("vocal") org.springframework.web.multipart.MultipartFile vocal,
        @org.springframework.web.bind.annotation.RequestPart("accompaniment") org.springframework.web.multipart.MultipartFile accompaniment,
        @org.springframework.web.bind.annotation.RequestParam("mode") String mode,
        @org.springframework.web.bind.annotation.RequestParam(value = "start_time", required = false) Double startTime,
        @org.springframework.web.bind.annotation.RequestParam(value = "end_time", required = false) Double endTime,
        @org.springframework.web.bind.annotation.RequestParam(value = "seed", required = false) Long seed
    );
}

class AiMediaWorkerClientFallback implements AiMediaWorkerClient {
    @Override
    public byte[] generateTts(Map<String, String> request) {
        throw new RuntimeException("AI Media Worker unavailable");
    }

    @Override
    public Map<String, Object> detectChorus(org.springframework.web.multipart.MultipartFile file) {
        throw new RuntimeException("AI Media Worker detect-chorus unavailable");
    }

    @Override
    public Map<String, Object> separateAudio(org.springframework.web.multipart.MultipartFile file, boolean excludeAudio) {
        throw new RuntimeException("AI Media Worker separate-audio unavailable");
    }

    @Override
    public byte[] mixAudio(org.springframework.web.multipart.MultipartFile vocal, org.springframework.web.multipart.MultipartFile accompaniment, String mode, Double startTime, Double endTime, Long seed) {
        throw new RuntimeException("AI Media Worker mix-audio unavailable");
    }
}
