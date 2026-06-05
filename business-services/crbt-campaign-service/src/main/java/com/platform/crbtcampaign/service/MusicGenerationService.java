package com.platform.crbtcampaign.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.ai.LyriaSystemPromptConfig;
import com.platform.common.core.exception.BaseException;
import com.platform.common.core.exception.CommonErrorCode;
import com.platform.common.core.response.ApiResponse;
import com.platform.crbtcampaign.client.AuthServiceClient;
import com.platform.crbtcampaign.client.FileServiceClient;
import com.platform.crbtcampaign.client.LyriaClient;
import com.platform.crbtcampaign.client.CreditWalletClient;
import com.platform.crbtcampaign.client.dto.UserCreditResponse;
import com.platform.crbtcampaign.client.dto.WalletAmountRequest;
import com.platform.crbtcampaign.client.AudioGenerationClient;
import com.platform.crbtcampaign.client.dto.DiyJobResponse;
import com.platform.crbtcampaign.dto.response.MyLibraryItemResponse;
import com.platform.crbtcampaign.entity.UserLyriaHistory;
import com.platform.crbtcampaign.repository.UserLyriaHistoryRepository;
import org.springframework.transaction.annotation.Transactional;
import com.platform.crbtcampaign.client.dto.WalletResponse;
import com.platform.crbtcampaign.dto.response.GenerateMusicResponse;
import com.platform.crbtcampaign.entity.UserSubscription;
import com.platform.crbtcampaign.exception.CampaignErrorCode;
import com.platform.crbtcampaign.repository.UserSubscriptionRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
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
    private final CreditWalletClient creditWalletClient;
    private final UserSubscriptionRepository subscriptionRepository;
    private final LyriaSystemPromptConfig promptConfig;
    private final RedisTemplate<String, String> redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final UserLyriaHistoryRepository historyRepository;
    private final AudioGenerationClient audioGenerationClient;
    private final Random random = new Random();

    public MusicGenerationService(AuthServiceClient authServiceClient,
                                  FileServiceClient fileServiceClient,
                                  LyriaClient lyriaClient,
                                  CreditWalletClient creditWalletClient,
                                  UserSubscriptionRepository subscriptionRepository,
                                  LyriaSystemPromptConfig promptConfig,
                                  RedisTemplate<String, String> redisTemplate,
                                  RabbitTemplate rabbitTemplate,
                                  ObjectMapper objectMapper,
                                  UserLyriaHistoryRepository historyRepository,
                                  AudioGenerationClient audioGenerationClient) {
        this.authServiceClient = authServiceClient;
        this.fileServiceClient = fileServiceClient;
        this.lyriaClient = lyriaClient;
        this.creditWalletClient = creditWalletClient;
        this.subscriptionRepository = subscriptionRepository;
        this.promptConfig = promptConfig;
        this.redisTemplate = redisTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.historyRepository = historyRepository;
        this.audioGenerationClient = audioGenerationClient;
    }


    public GenerateMusicResponse generate(String msisdn, String genre, String mood, String instrument) {
        log.info("[GENERATE-START] Starting music generation request for msisdn={}, genre={}, mood={}, instrument={}", 
            mask(msisdn), genre, mood, instrument);

        UserCreditResponse userInfo;
        try {
            log.info("[GENERATE-USER-CHECK] Fetching userId for msisdn={}", mask(msisdn));
            userInfo = authServiceClient.getUserCredit(msisdn);
            log.info("[GENERATE-USER-CHECK-OK] msisdn={} has userId={}", mask(msisdn), userInfo.userId());
        } catch (Exception e) {
            log.error("[GENERATE-USER-ERROR] Failed to check user for msisdn={}: {}", mask(msisdn), e.getMessage(), e);
            throw e;
        }

        Long userId = userInfo.userId();

        // 1. Fetch active subscription
        List<UserSubscription> activeSubs = subscriptionRepository.findByUserIdAndStatus(userId, UserSubscription.Status.ACTIVE);
        if (activeSubs.isEmpty()) {
            log.warn("[GENERATE-CREDIT-INSUFFICIENT] userId={} has no active subscription", userId);
            throw new BaseException(CampaignErrorCode.SUBSCRIBER_NOT_ACTIVE);
        }

        UserSubscription sub = activeSubs.get(0);
        if (sub.getExpiresAt().isBefore(Instant.now())) {
            log.warn("[GENERATE-CREDIT-INSUFFICIENT] userId={} active subscription has expired", userId);
            throw new BaseException(CampaignErrorCode.SUBSCRIBER_NOT_ACTIVE);
        }

        // 2. Fetch wallet balance
        int walletBalance = 0;
        try {
            ApiResponse<WalletResponse> balanceResp = creditWalletClient.getBalance(userId);
            if (balanceResp != null && balanceResp.success() && balanceResp.data() != null) {
                walletBalance = balanceResp.data().balance();
            }
        } catch (Exception e) {
            log.error("[GENERATE-CREDIT-ERROR] Failed to check wallet balance for userId={}: {}", userId, e.getMessage());
            throw new BaseException(CommonErrorCode.SYSTEM_BUSY);
        }

        if (walletBalance < 1) {
            log.warn("[GENERATE-CREDIT-INSUFFICIENT] userId={} wallet balance {} is insufficient", userId, walletBalance);
            throw new BaseException(CampaignErrorCode.INSUFFICIENT_TOKENS);
        }

        String txnRef = "MUSIC-" + UUID.randomUUID();

        // 3. Deduct credit synchronously on credit-wallet-service to keep in sync
        log.info("[GENERATE-DEDUCT] Deducting credit first before generation for userId={}, txnRef={}", userId, txnRef);
        deductCreditSynchronously(userId, genre, mood, instrument, txnRef);

        try {
            // Cache lookup
            String hashKey = sha256(genre + ":" + mood + ":" + instrument);
            String poolKey = POOL_KEY_PREFIX + hashKey;
            String seenKey = SEEN_KEY_PREFIX + msisdn + ":" + hashKey;

            log.info("[GENERATE-CACHE-LOOKUP] Looking up Redis cache for hashKey={}", hashKey);
            List<String> poolEntries = redisTemplate.opsForList().range(poolKey, 0, -1);
            Set<String> seenUrls = redisTemplate.opsForSet().members(seenKey);
            if (seenUrls == null) seenUrls = Set.of();
            log.info("[GENERATE-CACHE-STATS] Redis poolEntries size={}, seenUrls size={}", 
                poolEntries != null ? poolEntries.size() : 0, seenUrls.size());

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

            redisTemplate.opsForSet().add(seenKey, url);

            // Save to user history DB
            try {
                String title = generateTitle(genre, mood, instrument);
                UserLyriaHistory history = new UserLyriaHistory(userId, msisdn, title, genre, mood, instrument, url);
                historyRepository.save(history);
                log.info("[GENERATE-HISTORY-SAVE] Saved to user_lyria_history. userId={}, title={}, url={}", userId, title, url);
            } catch (Exception dbEx) {
                log.error("[GENERATE-HISTORY-ERROR] Failed to save history to DB: {}", dbEx.getMessage(), dbEx);
                // Do not fail the request if history database insertion fails
            }

            log.info("[GENERATE-SUCCESS] Successfully processed request for msisdn={}, returned url={}", mask(msisdn), url);
            return new GenerateMusicResponse(url);


        } catch (Exception e) {
            log.error("[GENERATE-FAILURE] Generation failed for userId={}. Refunding credit with txnRef={}", userId, txnRef, e);
            try {
                refundCreditSynchronously(userId, genre, mood, instrument, txnRef);
            } catch (Exception ex) {
                log.error("[REFUND-ERROR] Failed to refund credit for userId={}: {}", userId, ex.getMessage(), ex);
            }
            throw new BaseException(CommonErrorCode.SYSTEM_BUSY);
        }
    }

    private String generateAndCache(String msisdn, String genre, String mood, String instrument, String poolKey) {
        String prompt = promptConfig.buildPrompt(genre, mood, instrument);
        log.info("[LYRIA-GENERATE] msisdn={} genre={} mood={} instrument={}", mask(msisdn), genre, mood, instrument);

        byte[] audioBytes;
        try {
            audioBytes = lyriaClient.generateMusic(prompt);
            log.info("[LYRIA-GENERATE-OK] Gemini generated audio bytes count: {}", audioBytes != null ? audioBytes.length : 0);

            try {
                org.springframework.web.context.request.ServletRequestAttributes attributes = 
                    (org.springframework.web.context.request.ServletRequestAttributes) org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    jakarta.servlet.http.HttpServletRequest request = attributes.getRequest();
                    Object tokenUsageObj = request.getAttribute("lyria_token_usage");
                    if (tokenUsageObj instanceof Map) {
                        Map<String, Object> tokenUsage = new HashMap<>((Map<String, Object>) tokenUsageObj);
                        tokenUsage.put("msisdn", msisdn);
                        tokenUsage.put("model", "lyria-3-clip-preview");
                        tokenUsage.put("genre", genre);
                        tokenUsage.put("mood", mood);
                        tokenUsage.put("instrument", instrument);
                        request.setAttribute("lyria_token_usage", tokenUsage);
                    }
                }
            } catch (Exception ex) {
                log.warn("[LYRIA-ENRICH-WARN] Failed to enrich request attributes with token metadata: {}", ex.getMessage());
            }
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

    private void deductCreditSynchronously(Long userId, String genre, String mood, String instrument, String txnRef) {
        log.info("[WALLET-DEDUCT] Deducting credit synchronously for userId={}, txnRef={}", userId, txnRef);
        try {
            var request = new WalletAmountRequest(
                    1,
                    "AI Music: " + genre + "/" + mood + "/" + instrument,
                    txnRef
            );
            ApiResponse<WalletResponse> response = creditWalletClient.deduct(userId, request);
            if (response == null || !response.success() || response.data() == null) {
                throw new BaseException(CampaignErrorCode.CAMPAIGN_INSUFFICIENT_CREDIT);
            }
            log.info("[WALLET-DEDUCT-OK] Credit deducted successfully. New balance={}", response.data().balance());
        } catch (Exception e) {
            log.error("[WALLET-DEDUCT-ERROR] Failed to deduct credit for userId={}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    private void refundCreditSynchronously(Long userId, String genre, String mood, String instrument, String txnRef) {
        String refundRef = "REFUND-" + txnRef;
        log.info("[WALLET-REFUND] Refunding credit synchronously for userId={}, refundRef={}", userId, refundRef);
        try {
            var request = new WalletAmountRequest(
                    1,
                    "Refund: AI Music Generation failed (" + genre + "/" + mood + "/" + instrument + ")",
                    refundRef
            );
            ApiResponse<WalletResponse> response = creditWalletClient.add(userId, request);
            if (response == null || !response.success() || response.data() == null) {
                log.error("[WALLET-REFUND-FAILED] Failed response from credit-wallet-service during refund.");
            } else {
                log.info("[WALLET-REFUND-OK] Credit refunded successfully. New balance={}", response.data().balance());
            }
        } catch (Exception e) {
            log.error("[WALLET-REFUND-ERROR] Failed to call refund API for userId={}: {}", userId, e.getMessage(), e);
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

    public List<MyLibraryItemResponse> getMyLibrary(Long userId) {
        log.info("[MY-LIBRARY] Fetching library items for userId={}", userId);
        List<UserLyriaHistory> aiList = historyRepository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId);

        String authHeader = null;
        try {
            org.springframework.web.context.request.ServletRequestAttributes attributes = 
                (org.springframework.web.context.request.ServletRequestAttributes) org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                authHeader = attributes.getRequest().getHeader("Authorization");
            }
        } catch (Exception e) {
            log.warn("[MY-LIBRARY-AUTH-ERR] Failed to retrieve Authorization header: {}", e.getMessage());
        }

        List<DiyJobResponse> diyList = new ArrayList<>();
        if (authHeader != null) {
            try {
                ApiResponse<List<DiyJobResponse>> diyResp = audioGenerationClient.getUserJobs(authHeader);
                if (diyResp != null && diyResp.success() && diyResp.data() != null) {
                    diyList = diyResp.data();
                }
            } catch (Exception e) {
                log.error("[MY-LIBRARY-DIY-ERR] Feign call to audio-generation-service failed: {}", e.getMessage(), e);
            }
        }

        List<MyLibraryItemResponse> combined = new ArrayList<>();
        
        // Map AI history
        for (UserLyriaHistory item : aiList) {
            List<String> tags = new ArrayList<>();
            if (item.getGenre() != null && !item.getGenre().isBlank()) tags.add(item.getGenre().toLowerCase());
            if (item.getMood() != null && !item.getMood().isBlank()) tags.add(item.getMood().toLowerCase());
            if (item.getInstrument() != null && !item.getInstrument().isBlank()) tags.add(item.getInstrument().toLowerCase());
            tags.add(item.getDurationSeconds() + "s");

            combined.add(new MyLibraryItemResponse(
                "AI_" + item.getId(),
                item.getTitle(),
                "AI",
                tags,
                item.getAudioUrl(),
                item.getCreatedAt()
            ));
        }

        // Map DIY history (completed items only)
        for (DiyJobResponse item : diyList) {
            if (!"COMPLETED".equalsIgnoreCase(item.status())) {
                continue;
            }
            String url = item.resultUrl();
            if (url != null && url.contains(",")) {
                url = url.split(",")[0];
            }
            String displayTitle = "DIY Ringback Tone";
            if (item.prompt() != null && !item.prompt().isBlank()) {
                String cleaned = item.prompt().trim();
                displayTitle = cleaned.length() > 35 ? cleaned.substring(0, 32) + "..." : cleaned;
            }
            combined.add(new MyLibraryItemResponse(
                "DIY_" + item.id(),
                displayTitle,
                "DIY",
                List.of("diy", "mixed"),
                url,
                item.createdAt()
            ));
        }

        // Sort by createdAt DESC
        combined.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));
        return combined;
    }

    @Transactional
    public void deleteLibraryItem(Long userId, String unifiedId) {
        if (unifiedId == null) {
            throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST, "Invalid library item ID");
        }
        log.info("[DELETE-LIBRARY-ITEM] userId={}, unifiedId={}", userId, unifiedId);

        if (unifiedId.startsWith("AI_")) {
            Long historyId = Long.parseLong(unifiedId.substring(3));
            UserLyriaHistory history = historyRepository.findById(historyId)
                .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND));
            if (!history.getUserId().equals(userId)) {
                throw new BaseException(CommonErrorCode.COMMON_FORBIDDEN);
            }
            history.setDeleted(true);
            historyRepository.save(history);
            log.info("[DELETE-LIBRARY-ITEM-AI-OK] Soft-deleted AI music history record: {}", historyId);
        } else if (unifiedId.startsWith("DIY_")) {
            Long jobId = Long.parseLong(unifiedId.substring(4));
            String authHeader = null;
            try {
                org.springframework.web.context.request.ServletRequestAttributes attributes = 
                    (org.springframework.web.context.request.ServletRequestAttributes) org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    authHeader = attributes.getRequest().getHeader("Authorization");
                }
            } catch (Exception e) {
                log.warn("[DELETE-LIBRARY-ITEM-AUTH-ERR] Failed to retrieve Authorization header: {}", e.getMessage());
            }
            if (authHeader == null) {
                throw new BaseException(CommonErrorCode.COMMON_UNAUTHORIZED);
            }
            try {
                ApiResponse<Void> deleteResp = audioGenerationClient.deleteJob(authHeader, jobId);
                if (deleteResp == null || !deleteResp.success()) {
                    throw new BaseException(CommonErrorCode.SYSTEM_BUSY, "Failed to delete DIY job");
                }
                log.info("[DELETE-LIBRARY-ITEM-DIY-OK] Deleted DIY job: {}", jobId);
            } catch (Exception e) {
                log.error("[DELETE-LIBRARY-ITEM-DIY-ERR] Feign call to delete DIY job failed: {}", e.getMessage(), e);
                throw new BaseException(CommonErrorCode.SYSTEM_BUSY, "Cannot contact audio generation service");
            }
        } else {
            throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST, "Unknown library item type");
        }
    }

    private String generateTitle(String genre, String mood, String instrument) {
        String baseMood = (mood == null || mood.isBlank()) ? "Energetic" : mood.trim();
        String baseGenreOrInst = (instrument != null && !instrument.isBlank()) ? instrument.trim() : genre;
        if (baseGenreOrInst == null || baseGenreOrInst.isBlank()) {
            baseGenreOrInst = "Beat";
        } else {
            baseGenreOrInst = baseGenreOrInst.trim();
        }

        String[] musicalNouns = {"Vibes", "Melody", "Beats", "Groove", "Echoes", "Horizon", "Flow", "Dreams", "Journey", "Waves", "Rhythm"};
        int index = Math.abs((baseMood + baseGenreOrInst).hashCode()) % musicalNouns.length;
        String noun = musicalNouns[index];

        return capitalize(baseMood) + " " + capitalize(baseGenreOrInst) + " " + noun;
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return "";
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    private record PoolEntry(String url, String owner) {}

}
