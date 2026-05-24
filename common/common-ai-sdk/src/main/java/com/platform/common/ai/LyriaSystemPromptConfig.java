package com.platform.common.ai;

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
            - Provide a clean fade-friendly ending so the track loops smoothly.
            """;

    public String buildPrompt(String genre, String mood, String instrument) {
        return TEMPLATE.formatted(safe(genre), safe(mood), safe(instrument));
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unspecified" : value.trim();
    }
}
