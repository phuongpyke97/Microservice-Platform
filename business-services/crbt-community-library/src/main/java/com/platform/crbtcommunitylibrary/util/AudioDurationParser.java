package com.platform.crbtcommunitylibrary.util;

import com.mpatric.mp3agic.Mp3File;
import com.platform.common.core.exception.BaseException;
import com.platform.common.core.exception.CommonErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.core.io.FileSystemResource;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.Map;

@Component
public class AudioDurationParser {

    private static final Logger log = LoggerFactory.getLogger(AudioDurationParser.class);
    private static final int DEFAULT_DURATION = 30;

    @Value("${AI_WORKER_HOST:localhost}")
    private String aiWorkerHost;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Downloads and analyzes the audio file at the given URL.
     * Checks file size, duration, and vocal presence.
     */
    public AudioAnalysisResult analyzeAudio(String audioUrl) {
        if (audioUrl == null || audioUrl.isBlank()) {
            return new AudioAnalysisResult(DEFAULT_DURATION, 0L, false);
        }

        File tempFile = null;
        long sizeBytes = 0L;
        int durationSeconds = DEFAULT_DURATION;
        boolean hasVocal = false;

        try {
            // Establish connection and download the file content
            URL url = new URL(audioUrl);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            tempFile = File.createTempFile("audio-analysis-", ".tmp");
            try (InputStream is = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream fos = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }

            sizeBytes = tempFile.length();
            log.info("Downloaded audio file size: {} bytes from {}", sizeBytes, audioUrl);

            // 1. WAV parsing using Java Standard AudioSystem
            try {
                AudioFileFormat fileFormat = AudioSystem.getAudioFileFormat(tempFile);
                long frames = fileFormat.getFrameLength();
                float frameRate = fileFormat.getFormat().getFrameRate();
                if (frames > 0 && frameRate > 0) {
                    durationSeconds = Math.round(frames / frameRate);
                    log.info("Successfully extracted WAV duration: {} seconds", durationSeconds);
                }
            } catch (Exception e) {
                // Try MP3
                try {
                    Mp3File mp3File = new Mp3File(tempFile.getAbsolutePath());
                    durationSeconds = (int) mp3File.getLengthInSeconds();
                    log.info("Successfully extracted MP3 duration: {} seconds", durationSeconds);
                } catch (Exception ex) {
                    log.warn("Both WAV and MP3 duration parsers failed. Using default: {}s", DEFAULT_DURATION);
                }
            }

            // 2. Vocal detection via ai-media-worker
            hasVocal = detectVocalPresence(tempFile);

        } catch (Exception e) {
            log.error("Failed to analyze audio file from URL: {}", audioUrl, e);
            if (e instanceof BaseException) {
                throw (BaseException) e;
            }
        } finally {
            if (tempFile != null && tempFile.exists()) {
                try {
                    Files.delete(tempFile.toPath());
                } catch (Exception e) {
                    log.error("Failed to clean up temp file: {}", tempFile.getAbsolutePath(), e);
                }
            }
        }

        return new AudioAnalysisResult(durationSeconds, sizeBytes, hasVocal);
    }

    private boolean detectVocalPresence(File file) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new FileSystemResource(file));

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            String url = "http://" + aiWorkerHost + ":8765/separate-audio";

            log.info("Sending vocal detection request to: {}", url);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object hasVocalObj = response.getBody().get("has_vocal");
                if (hasVocalObj instanceof Boolean) {
                    boolean result = (Boolean) hasVocalObj;
                    log.info("Vocal detection result: has_vocal={}", result);
                    return result;
                } else if (hasVocalObj instanceof String) {
                    boolean result = Boolean.parseBoolean((String) hasVocalObj);
                    log.info("Vocal detection result: has_vocal={}", result);
                    return result;
                }
            }
        } catch (Exception e) {
            log.error("Vocal detection connection failed. Host: {}. Error: {}", aiWorkerHost, e.getMessage());
            // In case of communication failure, fallback to false but log the error
        }
        return false;
    }
}
