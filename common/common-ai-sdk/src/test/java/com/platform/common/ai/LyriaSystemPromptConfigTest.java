package com.platform.common.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LyriaSystemPromptConfigTest {

    private final LyriaSystemPromptConfig config = new LyriaSystemPromptConfig();

    @Test
    void buildPrompt_includesGenreMoodInstrument() {
        String prompt = config.buildPrompt("pop", "happy", "guitar");

        assertThat(prompt).contains("genre=pop");
        assertThat(prompt).contains("mood=happy");
        assertThat(prompt).contains("primary instrument=guitar");
    }

    @Test
    void buildPrompt_nullValuesDefaultToUnspecified() {
        String prompt = config.buildPrompt(null, null, null);

        assertThat(prompt).contains("genre=unspecified");
        assertThat(prompt).contains("mood=unspecified");
        assertThat(prompt).contains("primary instrument=unspecified");
    }

    @Test
    void buildPrompt_blankValueDefaultsToUnspecified() {
        String prompt = config.buildPrompt("  ", "  ", "  ");

        assertThat(prompt).contains("genre=unspecified");
    }

    @Test
    void buildPrompt_trimsSurroundingWhitespace() {
        String prompt = config.buildPrompt("  jazz  ", "calm", "piano");

        assertThat(prompt).contains("genre=jazz");
    }
}
