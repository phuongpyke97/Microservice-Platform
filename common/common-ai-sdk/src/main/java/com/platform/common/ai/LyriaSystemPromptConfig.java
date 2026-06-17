package com.platform.common.ai;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Component;

/**
 * System prompt for Google Gemini Lyria 3.
 * Output spec per Architecture.md: 30 s CRBT-ready instrumental, mono or stereo, loop-friendly tail.
 */
@Component
public class LyriaSystemPromptConfig {

    private static final String TEMPLATE = """
            You are Lyria 3, generating short ringtones for Mytel CRBT.
            Constraints:
            - Output exactly one 30-second instrumental track.
            - No vocals, no speech, no copyrighted melodies.
            - Match genre=%s, mood=%s, primary instrument=%s.
            - Vary the arrangement: tempo around %d BPM, key of %s.
            - Instrumentation details: %s.
            - Tempo and groove feel: %s.
            - Acoustic environment/vibe: %s.
            - Structure the arrangement with a fresh melodic motif and a distinct chord progression.
            - Provide a clean fade-friendly ending so the track loops smoothly.
            """;

    /**
     * Per-generation variation. Different values steer Lyria toward a distinct
     * arrangement even when genre/mood/instrument are identical.
     *
     * @param bpm  target tempo in beats per minute
     * @param key  musical key, e.g. "C major"
     * @param seed RNG seed forwarded to the Lyria generationConfig
     * @param secondaryInstrumentation randomized secondary instrument accompaniment
     * @param tempoGroove randomized tempo/groove feel description
     * @param acousticEnvironment randomized acoustic space/vibe description
     */
    public record MusicVariation(
        int bpm,
        String key,
        long seed,
        String secondaryInstrumentation,
        String tempoGroove,
        String acousticEnvironment
    ) {

        private static final String[] KEYS = {
            "C major", "G major", "D major", "A major", "E major",
            "F major", "B-flat major", "A minor", "E minor", "D minor"
        };

        private static final String[] SECONDARY_INSTRUMENTATIONS = {
            "solo acoustic guitar and soft flute",
            "electric piano and a gentle string quartet",
            "ambient synthesizer pads and a soft marimba",
            "acoustic ukulele and bright upright piano",
            "subtle acoustic guitar and delicate glockenspiel",
            "warm electric guitar and vintage electric piano"
        };

        private static final String[] TEMPO_GROOVES = {
            "relaxed groove with a slow tempo",
            "upbeat bouncy rhythm with a mid-tempo feel",
            "energetic driving beat with a fast tempo",
            "soft laid-back percussion with a gentle pulse",
            "smooth steady rhythm with a moderate tempo"
        };

        private static final String[] ACOUSTIC_ENVIRONMENTS = {
            "intimate lo-fi living room session vibe",
            "cinematic grand spacious festival vibe",
            "warm cozy studio recording feel",
            "dreamy spacious reverb and airy atmosphere",
            "clean bright modern production style"
        };

        private static final int MIN_BPM = 90;
        private static final int MAX_BPM = 140;

        /** Build a fresh random variation for one generation call. */
        public static MusicVariation random() {
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            int bpm = rng.nextInt(MIN_BPM, MAX_BPM + 1);
            String key = KEYS[rng.nextInt(KEYS.length)];
            long seed = rng.nextLong(1, Integer.MAX_VALUE);
            String secondaryInstrumentation = SECONDARY_INSTRUMENTATIONS[rng.nextInt(SECONDARY_INSTRUMENTATIONS.length)];
            String tempoGroove = TEMPO_GROOVES[rng.nextInt(TEMPO_GROOVES.length)];
            String acousticEnvironment = ACOUSTIC_ENVIRONMENTS[rng.nextInt(ACOUSTIC_ENVIRONMENTS.length)];
            return new MusicVariation(bpm, key, seed, secondaryInstrumentation, tempoGroove, acousticEnvironment);
        }
    }

    /** Backward-compatible prompt with a fixed, neutral arrangement. */
    public String buildPrompt(String genre, String mood, String instrument) {
        return buildPrompt(genre, mood, instrument, new MusicVariation(
            110, "C major", 0L, "standard instrumentation", "steady rhythm", "studio vibe"
        ));
    }

    /** Prompt that bakes in a per-generation {@link MusicVariation} for output diversity. */
    public String buildPrompt(String genre, String mood, String instrument, MusicVariation variation) {
        return TEMPLATE.formatted(
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
