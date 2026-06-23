package com.platform.crbtcampaign.service;

import com.platform.common.core.exception.BaseException;
import com.platform.common.core.exception.CommonErrorCode;
import com.platform.common.security.SecurityUtils;
import com.platform.crbtcampaign.dto.request.UpdateLyriaPromptRequest;
import com.platform.crbtcampaign.dto.response.LyriaPromptResponse;
import com.platform.crbtcampaign.dto.response.LyriaPromptVersionResponse;
import com.platform.crbtcampaign.entity.LyriaPromptConfig;
import com.platform.crbtcampaign.repository.LyriaPromptConfigRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LyriaPromptAdminService {

    static final Set<String> VALID_MODELS = Set.of("lyria-3-clip-preview", "lyria-3-pro-preview");
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";

    private final LyriaPromptConfigRepository repository;

    public LyriaPromptAdminService(LyriaPromptConfigRepository repository) {
        this.repository = repository;
    }

    /** Active version of a model (fallback stub if model has no row yet). */
    @Transactional(readOnly = true)
    public LyriaPromptResponse getActive(String model) {
        validateModel(model);
        LyriaPromptConfig config = repository.findFirstByModelAndStatusOrderByVersionDesc(model, STATUS_ACTIVE)
                .orElseGet(() -> defaultStub(model));
        return mapToResponse(config);
    }

    /** History rows. {@code modelFilter} = null/"ALL" → all models; else single model. ACTIVE-first then createdAt desc. */
    @Transactional(readOnly = true)
    public List<LyriaPromptVersionResponse> listHistory(String modelFilter) {
        List<LyriaPromptConfig> rows;
        if (modelFilter == null || modelFilter.isBlank() || "ALL".equalsIgnoreCase(modelFilter)) {
            rows = repository.findAllByOrderByModelAscVersionDesc();
        } else {
            validateModel(modelFilter);
            rows = repository.findByModelOrderByVersionDesc(modelFilter);
        }
        return rows.stream()
                .sorted(Comparator
                        .comparing((LyriaPromptConfig c) -> STATUS_ACTIVE.equals(c.getStatus()) ? 0 : 1)
                        .thenComparing(LyriaPromptConfig::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::mapToVersionRow)
                .collect(Collectors.toList());
    }

    /** Full payload of one specific version (used by the "Xem" button). */
    @Transactional(readOnly = true)
    public LyriaPromptResponse getVersion(String model, int version) {
        validateModel(model);
        LyriaPromptConfig config = repository.findByModelAndVersion(model, version)
                .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND,
                        "Không tìm thấy phiên bản v" + version + " của model " + model));
        return mapToResponse(config);
    }

    /** Save editor config as a NEW version for the model and activate it. */
    @Transactional
    @CacheEvict(value = "lyria_prompts", allEntries = true)
    public LyriaPromptResponse saveNewVersion(UpdateLyriaPromptRequest request) {
        String model = request.model();
        validateModel(model);
        validateTemplate(request.promptTemplate());

        Instant now = Instant.now();
        // Deactivate current active version of this model.
        repository.findFirstByModelAndStatusOrderByVersionDesc(model, STATUS_ACTIVE)
                .ifPresent(existing -> {
                    existing.setStatus(STATUS_INACTIVE);
                    existing.setDeactivatedAt(now);
                    repository.save(existing);
                });

        int nextVersion = repository.findTopByModelOrderByVersionDesc(model)
                .map(c -> c.getVersion() + 1)
                .orElse(1);

        LyriaPromptConfig newConfig = new LyriaPromptConfig(
                model,
                nextVersion,
                request.promptTemplate(),
                request.keys(),
                request.secondaryInstrumentations(),
                request.tempoGrooves(),
                request.acousticEnvironments(),
                STATUS_ACTIVE,
                currentUser()
        );
        newConfig.setActivatedAt(now);

        return mapToResponse(repository.save(newConfig));
    }

    /** Activate an existing past version of a model. */
    @Transactional
    @CacheEvict(value = "lyria_prompts", allEntries = true)
    public LyriaPromptResponse activateVersion(String model, int version) {
        validateModel(model);
        LyriaPromptConfig target = repository.findByModelAndVersion(model, version)
                .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND,
                        "Không tìm thấy phiên bản v" + version + " của model " + model));

        Instant now = Instant.now();
        repository.findFirstByModelAndStatusOrderByVersionDesc(model, STATUS_ACTIVE)
                .filter(active -> active != target)
                .ifPresent(active -> {
                    active.setStatus(STATUS_INACTIVE);
                    active.setDeactivatedAt(now);
                    repository.save(active);
                });

        target.setStatus(STATUS_ACTIVE);
        target.setActivatedAt(now);
        target.setDeactivatedAt(null);
        return mapToResponse(repository.save(target));
    }

    // ---- helpers ----

    private void validateModel(String model) {
        if (model == null || !VALID_MODELS.contains(model)) {
            throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST,
                    "Model không hợp lệ. Chỉ chấp nhận: " + VALID_MODELS);
        }
    }

    private void validateTemplate(String template) {
        int sCount = countOccurrences(template, "%s");
        int dCount = countOccurrences(template, "%d");
        if (sCount != 7 || dCount != 1) {
            throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST,
                    "Cấu hình template không hợp lệ. Phải chứa chính xác 7 biến chuỗi (%s) và 1 biến số nguyên (%d).");
        }
    }

    private String currentUser() {
        return SecurityUtils.getCurrentUserId() != null ? String.valueOf(SecurityUtils.getCurrentUserId()) : "ADMIN";
    }

    private int countOccurrences(String text, String target) {
        if (text == null || target == null || text.isEmpty() || target.isEmpty()) {
            return 0;
        }
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(target, idx)) != -1) {
            count++;
            idx += target.length();
        }
        return count;
    }

    private LyriaPromptConfig defaultStub(String model) {
        return new LyriaPromptConfig(
                model,
                1,
                "You are Lyria 3, generating short ringtones for Mytel CRBT. Constraints: - Output exactly one 30-second instrumental track. - No vocals, no speech, no copyrighted melodies. - Match genre=%s, mood=%s, primary instrument=%s. - Vary the arrangement: tempo around %d BPM, key of %s. - Instrumentation details: %s. - Tempo and groove feel: %s. - Acoustic environment/vibe: %s. - Structure the arrangement with a fresh melodic motif and a distinct chord progression. - Provide a clean fade-friendly ending so the track loops smoothly. - Quality enforcement: Strictly instrumental, no vocals, High fidelity, 48kHz, radio-ready mixing, clean mastering, catchy ringtone melody, balanced EQ, avoiding harsh treble.",
                List.of("C major", "G major", "D major", "A major", "E major", "F major", "B-flat major", "A minor", "E minor", "D minor"),
                List.of("solo acoustic guitar and soft flute", "electric piano and a gentle string quartet", "ambient synthesizer pads and a soft marimba", "acoustic ukulele and bright upright piano", "subtle acoustic guitar and delicate glockenspiel", "warm electric guitar and vintage electric piano"),
                List.of("relaxed groove with a slow tempo", "upbeat bouncy rhythm with a mid-tempo feel", "energetic driving beat with a fast tempo", "soft laid-back percussion with a gentle pulse", "smooth steady rhythm with a moderate tempo"),
                List.of("intimate lo-fi living room session vibe", "cinematic grand spacious festival vibe", "warm cozy studio recording feel", "dreamy spacious reverb and airy atmosphere", "clean bright modern production style"),
                STATUS_ACTIVE,
                "SYSTEM"
        );
    }

    private LyriaPromptResponse mapToResponse(LyriaPromptConfig c) {
        return new LyriaPromptResponse(
                c.getId(),
                c.getModel(),
                c.getVersion(),
                c.getPromptTemplate(),
                c.getKeys(),
                c.getSecondaryInstrumentations(),
                c.getTempoGrooves(),
                c.getAcousticEnvironments(),
                c.getStatus(),
                c.getCreatedBy(),
                c.getCreatedAt(),
                c.getActivatedAt(),
                c.getDeactivatedAt()
        );
    }

    private LyriaPromptVersionResponse mapToVersionRow(LyriaPromptConfig c) {
        return new LyriaPromptVersionResponse(
                c.getId(),
                c.getModel(),
                c.getVersion(),
                c.getStatus(),
                c.getCreatedBy(),
                c.getCreatedAt(),
                c.getActivatedAt(),
                c.getDeactivatedAt()
        );
    }
}
