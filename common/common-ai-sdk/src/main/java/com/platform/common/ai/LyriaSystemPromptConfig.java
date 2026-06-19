package com.platform.common.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * System prompt for Google Gemini Lyria 3.
 * Output spec per Architecture.md: 30 s CRBT-ready instrumental, mono or stereo, loop-friendly tail.
 */
@Component
public class LyriaSystemPromptConfig {

    private static final Logger log = LoggerFactory.getLogger(LyriaSystemPromptConfig.class);

    private static final String DEFAULT_TEMPLATE = "You are Lyria 3, generating short ringtones for Mytel CRBT. Constraints: - Output exactly one 30-second instrumental track. - No vocals, no speech, no copyrighted melodies. - Match genre=%s, mood=%s, primary instrument=%s. - Vary the arrangement: tempo around %d BPM, key of %s. - Instrumentation details: %s. - Tempo and groove feel: %s. - Acoustic environment/vibe: %s. - Structure the arrangement with a fresh melodic motif and a distinct chord progression. - Provide a clean fade-friendly ending so the track loops smoothly. - Quality enforcement: Strictly instrumental, no vocals, High fidelity, 48kHz, radio-ready mixing, clean mastering, catchy ringtone melody, balanced EQ, avoiding harsh treble.";

    private static final String[] DEFAULT_KEYS = {
        "C major", "G major", "D major", "A major", "E major",
        "F major", "B-flat major", "A minor", "E minor", "D minor"
    };

    private static final String[] DEFAULT_SECONDARY_INSTRUMENTATIONS = {
        "solo acoustic guitar and soft flute",
        "electric piano and a gentle string quartet",
        "ambient synthesizer pads and a soft marimba",
        "acoustic ukulele and bright upright piano",
        "subtle acoustic guitar and delicate glockenspiel",
        "warm electric guitar and vintage electric piano"
    };

    private static final String[] DEFAULT_TEMPO_GROOVES = {
        "relaxed groove with a slow tempo",
        "upbeat bouncy rhythm with a mid-tempo feel",
        "energetic driving beat with a fast tempo",
        "soft laid-back percussion with a gentle pulse",
        "smooth steady rhythm with a moderate tempo"
    };

    private static final String[] DEFAULT_ACOUSTIC_ENVIRONMENTS = {
        "intimate lo-fi living room session vibe",
        "cinematic grand spacious festival vibe",
        "warm cozy studio recording feel",
        "dreamy spacious reverb and airy atmosphere",
        "clean bright modern production style"
    };

    private String template = DEFAULT_TEMPLATE;
    private String[] keys = DEFAULT_KEYS;
    private String[] secondaryInstrumentations = DEFAULT_SECONDARY_INSTRUMENTATIONS;
    private String[] tempoGrooves = DEFAULT_TEMPO_GROOVES;
    private String[] acousticEnvironments = DEFAULT_ACOUSTIC_ENVIRONMENTS;

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource("prompttemplate.json");
            if (resource.exists()) {
                ObjectMapper mapper = new ObjectMapper();
                try (java.io.InputStream is = resource.getInputStream()) {
                    com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(is);
                    
                    if (root.has("template")) {
                        this.template = root.get("template").asText();
                    }
                    if (root.has("keys") && root.get("keys").isArray()) {
                        this.keys = mapper.convertValue(root.get("keys"), String[].class);
                    }
                    if (root.has("secondaryInstrumentations") && root.get("secondaryInstrumentations").isArray()) {
                        this.secondaryInstrumentations = mapper.convertValue(root.get("secondaryInstrumentations"), String[].class);
                    }
                    if (root.has("tempoGrooves") && root.get("tempoGrooves").isArray()) {
                        this.tempoGrooves = mapper.convertValue(root.get("tempoGrooves"), String[].class);
                    }
                    if (root.has("acousticEnvironments") && root.get("acousticEnvironments").isArray()) {
                        this.acousticEnvironments = mapper.convertValue(root.get("acousticEnvironments"), String[].class);
                    }
                    log.info("[LYRIA-PROMPT-INIT] Loaded prompt template and variations successfully from prompttemplate.json");
                }
            } else {
                log.info("[LYRIA-PROMPT-INIT] prompttemplate.json not found on classpath, using default configuration.");
            }
        } catch (Exception e) {
            log.error("[LYRIA-PROMPT-INIT] Failed to load prompttemplate.json, falling back to defaults: {}", e.getMessage(), e);
        }
    }

    /**
     * Per-generation variation. Different values steer Lyria toward a distinct
     * arrangement even when genre/mood/instrument are identical.
     */
    public record MusicVariation(
        int bpm,
        String key,
        long seed,
        String secondaryInstrumentation,
        String tempoGroove,
        String acousticEnvironment
    ) {
        // Kept for backward compatibility with tests
        @Deprecated
        public static MusicVariation random() {
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            int bpm = rng.nextInt(90, 141);
            String key = DEFAULT_KEYS[rng.nextInt(DEFAULT_KEYS.length)];
            long seed = rng.nextLong(1, Integer.MAX_VALUE);
            String secondaryInstrumentation = DEFAULT_SECONDARY_INSTRUMENTATIONS[rng.nextInt(DEFAULT_SECONDARY_INSTRUMENTATIONS.length)];
            String tempoGroove = DEFAULT_TEMPO_GROOVES[rng.nextInt(DEFAULT_TEMPO_GROOVES.length)];
            String acousticEnvironment = DEFAULT_ACOUSTIC_ENVIRONMENTS[rng.nextInt(DEFAULT_ACOUSTIC_ENVIRONMENTS.length)];
            return new MusicVariation(bpm, key, seed, secondaryInstrumentation, tempoGroove, acousticEnvironment);
        }
    }

    /** Build a fresh random variation for one generation call using loaded configurations. */
    public MusicVariation randomVariation() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int bpm = rng.nextInt(90, 141);
        String key = keys[rng.nextInt(keys.length)];
        long seed = rng.nextLong(1, Integer.MAX_VALUE);
        String secondaryInstrumentation = secondaryInstrumentations[rng.nextInt(secondaryInstrumentations.length)];
        String tempoGroove = tempoGrooves[rng.nextInt(tempoGrooves.length)];
        String acousticEnvironment = acousticEnvironments[rng.nextInt(acousticEnvironments.length)];
        return new MusicVariation(bpm, key, seed, secondaryInstrumentation, tempoGroove, acousticEnvironment);
    }

    /** Backward-compatible prompt with a fixed, neutral arrangement. */
    public String buildPrompt(String genre, String mood, String instrument) {
        return buildPrompt(genre, mood, instrument, new MusicVariation(
            110, "C major", 0L, "standard instrumentation", "steady rhythm", "studio vibe"
        ));
    }

    /** Prompt that bakes in a per-generation {@link MusicVariation} for output diversity. */
    public String buildPrompt(String genre, String mood, String instrument, MusicVariation variation) {
        return this.template.formatted(
            safe(genre), safe(mood), safe(instrument),
            variation.bpm(), safe(variation.key()),
            variation.secondaryInstrumentation(),
            variation.tempoGroove(),
            variation.acousticEnvironment()
        );
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unspecified" : value.trim();
    }
}
