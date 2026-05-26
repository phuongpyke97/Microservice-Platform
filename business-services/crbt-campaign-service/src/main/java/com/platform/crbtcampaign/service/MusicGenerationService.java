package com.platform.crbtcampaign.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.ai.LyriaSystemPromptConfig;
import com.platform.common.core.exception.BaseException;
import com.platform.common.rmq.RmqExchanges;
import com.platform.common.rmq.RmqRoutingKeys;
import com.platform.common.rmq.event.CreditChangedEvent;
import com.platform.crbtcampaign.client.AuthServiceClient;
import com.platform.crbtcampaign.client.FileServiceClient;
import com.platform.crbtcampaign.client.LyriaClient;
import com.platform.crbtcampaign.client.dto.UserCreditResponse;
import com.platform.crbtcampaign.dto.response.GenerateMusicResponse;
import com.platform.crbtcampaign.exception.CampaignErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class MusicGenerationService {

    private static final Logger log = LoggerFactory.getLogger(MusicGenerationService.class);
    private static final int POOL_MAX_SIZE = 100;
    private static final String POOL_KEY_PREFIX = "lyria:pool:";
    private static final String SEEN_KEY_PREFIX = "lyria:seen:";

    private final AuthServiceClient authServiceClient;
    private final FileServiceClient fileServiceClient;
    private final LyriaClient lyriaClient;
    private final LyriaSystemPromptConfig promptConfig;
    private final RedisTemplate<String, String> redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    public MusicGenerationService(AuthServiceClient authServiceClient,
                                  FileServiceClient fileServiceClient,
                                  LyriaClient lyriaClient,
                                  LyriaSystemPromptConfig promptConfig,
                                  RedisTemplate<String, String> redisTemplate,
                                  RabbitTemplate rabbitTemplate,
                                  ObjectMapper objectMapper) {
        this.authServiceClient = authServiceClient;
        this.fileServiceClient = fileServiceClient;
        this.lyriaClient = lyriaClient;
        this.promptConfig = promptConfig;
        this.redisTemplate = redisTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    public GenerateMusicResponse generate(String msisdn, String genre, String mood, String instrument) {
        log.info("[GENERATE-START] Starting music generation request for msisdn={}, genre={}, mood={}, instrument={}", 
            mask(msisdn), genre, mood, instrument);

        UserCreditResponse credit;
        try {
            log.info("[GENERATE-CREDIT-CHECK] Fetching credit for msisdn={}", mask(msisdn));
            credit = authServiceClient.getUserCredit(msisdn);
            log.info("[GENERATE-CREDIT-CHECK-OK] msisdn={} has userId={}, creditBalance={}", 
                mask(msisdn), credit.userId(), credit.creditBalance());
        } catch (Exception e) {
            log.error("[GENERATE-CREDIT-ERROR] Failed to check credit for msisdn={}: {}", mask(msisdn), e.getMessage(), e);
            throw e;
        }

        if (credit.creditBalance() < 1) {
            log.warn("[GENERATE-CREDIT-INSUFFICIENT] msisdn={} credit balance {} is insufficient", mask(msisdn), credit.creditBalance());
            throw new BaseException(CampaignErrorCode.CAMPAIGN_INSUFFICIENT_CREDIT);
        }
        Long userId = credit.userId();

        // Step 3: cache lookup
        String hashKey = sha256(genre + ":" + mood + ":" + instrument);
        String poolKey = POOL_KEY_PREFIX + hashKey;
        String seenKey = SEEN_KEY_PREFIX + msisdn + ":" + hashKey;

        log.info("[GENERATE-CACHE-LOOKUP] Looking up Redis cache for hashKey={}", hashKey);
        List<String> poolEntries;
        Set<String> seenUrls;
        try {
            poolEntries = redisTemplate.opsForList().range(poolKey, 0, -1);
            seenUrls = redisTemplate.opsForSet().members(seenKey);
            if (seenUrls == null) seenUrls = Set.of();
            log.info("[GENERATE-CACHE-STATS] Redis poolEntries size={}, seenUrls size={}", 
                poolEntries != null ? poolEntries.size() : 0, seenUrls.size());
        } catch (Exception e) {
            log.error("[GENERATE-CACHE-ERROR] Failed to query Redis cache: {}", e.getMessage(), e);
            throw e;
        }

        List<String> available = buildAvailable(poolEntries, seenUrls, msisdn);
        log.info("[GENERATE-CACHE-AVAILABLE] Available candidate tracks count: {}", available.size());

        String url;
        if (!available.isEmpty()) {
            url = available.get(random.nextInt(available.size()));
            log.info("[LYRIA-CACHE-HIT] msisdn={} hashKey={} url={}", mask(msisdn), hashKey, url);
        } else {
            log.info("[LYRIA-CACHE-MISS] No available tracks in cache pool for hashKey={}. Calling AI generation...", hashKey);
            url = generateAndCache(msisdn, genre, mood, instrument, poolKey);
        }

        try {
            log.info("[GENERATE-FINAL-STEPS] Marking url as seen and publishing credit deduction for userId={}", userId);
            redisTemplate.opsForSet().add(seenKey, url);
            publishCreditDeduction(userId, genre, mood, instrument);
            log.info("[GENERATE-SUCCESS] Successfully processed request for msisdn={}, returned url={}", mask(msisdn), url);
        } catch (Exception e) {
            log.error("[GENERATE-FINAL-ERROR] Failed during final steps (Redis seen / RabbitMQ publish): {}", e.getMessage(), e);
            throw e;
        }

        return new GenerateMusicResponse(url);
    }

    private String generateAndCache(String msisdn, String genre, String mood, String instrument, String poolKey) {
        String prompt = promptConfig.buildPrompt(genre, mood, instrument);
        log.info("[LYRIA-GENERATE] msisdn={} genre={} mood={} instrument={}", mask(msisdn), genre, mood, instrument);

        byte[] audioBytes;
        try {
            audioBytes = lyriaClient.generateMusic(prompt);
            log.info("[LYRIA-GENERATE-OK] Gemini generated audio bytes count: {}", audioBytes != null ? audioBytes.length : 0);
        } catch (Exception e) {
            log.error("[LYRIA-GENERATE-FAILED] Gemini Lyria music generation failed: {}", e.getMessage(), e);
            throw e;
        }

        String url;
        try {
            log.info("[LYRIA-UPLOAD-START] Uploading generated audio bytes to MinIO...");
            url = fileServiceClient.uploadAudio(audioBytes, "media-audio").data();
            if (url == null || url.isBlank()) {
                throw new BaseException(CampaignErrorCode.CAMPAIGN_FILE_UPLOAD_FAILED);
            }
            log.info("[LYRIA-UPLOAD-OK] Uploaded successfully, MinIO url={}", url);
        } catch (Exception e) {
            log.error("[LYRIA-UPLOAD-FAILED] Failed to upload audio to file-service: {}", e.getMessage(), e);
            throw e;
        }

        try {
            String entry = toJson(new PoolEntry(url, msisdn));
            redisTemplate.opsForList().rightPush(poolKey, entry);
            Long poolSize = redisTemplate.opsForList().size(poolKey);
            if (poolSize != null && poolSize > POOL_MAX_SIZE) {
                redisTemplate.opsForList().trim(poolKey, poolSize - POOL_MAX_SIZE, -1);
            }
            log.info("[LYRIA-CACHED] Saved URL to pool. msisdn={} url={}", mask(msisdn), url);
        } catch (Exception e) {
            log.error("[LYRIA-CACHE-SAVE-FAILED] Failed to save generated track info to Redis pool: {}", e.getMessage(), e);
        }

        return url;
    }

    private List<String> buildAvailable(List<String> poolEntries, Set<String> seenUrls, String msisdn) {
        if (poolEntries == null || poolEntries.isEmpty()) return List.of();
        List<String> available = new ArrayList<>();
        for (String entry : poolEntries) {
            try {
                PoolEntry pe = objectMapper.readValue(entry, PoolEntry.class);
                if (!msisdn.equals(pe.owner()) && !seenUrls.contains(pe.url())) {
                    available.add(pe.url());
                }
            } catch (JsonProcessingException e) {
                log.warn("[LYRIA-POOL] Skipping malformed entry: {}", entry);
            }
        }
        return available;
    }

    private void publishCreditDeduction(Long userId, String genre, String mood, String instrument) {
        CreditChangedEvent event = new CreditChangedEvent(
                userId,
                1,
                "OUT",
                "AI Music: " + genre + "/" + mood + "/" + instrument,
                "MUSIC-" + UUID.randomUUID(),
                System.currentTimeMillis()
        );
        log.info("[RABBITMQ-PUBLISH] Publishing CreditChangedEvent for userId={} through RabbitMQ...", userId);
        try {
            rabbitTemplate.convertAndSend(RmqExchanges.CREDIT_EVENTS, RmqRoutingKeys.CREDIT_CHANGED, event);
            log.info("[RABBITMQ-PUBLISH-OK] CreditChangedEvent published successfully.");
        } catch (Exception e) {
            log.error("[RABBITMQ-PUBLISH-ERROR] Failed to publish CreditChangedEvent to RabbitMQ: {}", e.getMessage(), e);
            throw e;
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String toJson(PoolEntry entry) {
        try {
            return objectMapper.writeValueAsString(entry);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize pool entry", e);
        }
    }

    private String mask(String msisdn) {
        if (msisdn == null || msisdn.length() <= 4) return "***";
        return msisdn.substring(0, 3) + "***" + msisdn.substring(msisdn.length() - 2);
    }

    private record PoolEntry(String url, String owner) {}
}
