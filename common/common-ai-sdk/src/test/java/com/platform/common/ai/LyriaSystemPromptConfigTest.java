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

    @Test
    void buildPrompt_withVariation_includesVariationDetails() {
        LyriaSystemPromptConfig.MusicVariation variation = new LyriaSystemPromptConfig.MusicVariation(
            120, "D major", 42L, "flute and harp", "swing rhythm", "concert hall vibe"
        );
        String prompt = config.buildPrompt("jazz", "happy", "piano", variation);

        assertThat(prompt).contains("tempo around 120 BPM");
        assertThat(prompt).contains("key of D major");
        assertThat(prompt).contains("Instrumentation details: flute and harp");
        assertThat(prompt).contains("Tempo and groove feel: swing rhythm");
        assertThat(prompt).contains("Acoustic environment/vibe: concert hall vibe");
    }

    @Test
    void buildPrompt_withModel_usesModelSpecificProviderTemplate() {
        // G1: when a model is supplied, resolution must read that model's prompt,
        // not the default-model prompt.
        LyriaPromptProvider mockProvider = org.mockito.Mockito.mock(LyriaPromptProvider.class);
        org.mockito.Mockito.when(mockProvider.getTemplate("lyria-3-pro"))
            .thenReturn("PRO template: genre=%s mood=%s instr=%s bpm=%d key=%s sec=%s groove=%s env=%s");
        org.mockito.Mockito.when(mockProvider.getKeys("lyria-3-pro")).thenReturn(java.util.List.of("F minor"));
        org.mockito.Mockito.when(mockProvider.getSecondaryInstrumentations("lyria-3-pro")).thenReturn(java.util.List.of("grand piano"));
        org.mockito.Mockito.when(mockProvider.getTempoGrooves("lyria-3-pro")).thenReturn(java.util.List.of("slow groove"));
        org.mockito.Mockito.when(mockProvider.getAcousticEnvironments("lyria-3-pro")).thenReturn(java.util.List.of("studio vibe"));

        LyriaSystemPromptConfig customConfig = new LyriaSystemPromptConfig();
        customConfig.setProvider(mockProvider);

        LyriaSystemPromptConfig.MusicVariation variation = customConfig.randomVariation("lyria-3-pro");
        String prompt = customConfig.buildPrompt("jazz", "happy", "piano", variation, "lyria-3-pro");

        assertThat(prompt).contains("PRO template:");
        assertThat(prompt).contains("key=F minor");
        assertThat(prompt).contains("sec=grand piano");
        assertThat(prompt).contains("groove=slow groove");
        assertThat(prompt).contains("env=studio vibe");
        org.mockito.Mockito.verify(mockProvider).getTemplate("lyria-3-pro");
    }

    @Test
    void buildPrompt_withProvider_usesProviderTemplate() {
        LyriaPromptProvider mockProvider = org.mockito.Mockito.mock(LyriaPromptProvider.class);
        org.mockito.Mockito.when(mockProvider.getTemplate()).thenReturn("Custom template: genre=%s mood=%s instr=%s bpm=%d key=%s sec=%s groove=%s env=%s");
        org.mockito.Mockito.when(mockProvider.getKeys()).thenReturn(java.util.List.of("G major"));
        org.mockito.Mockito.when(mockProvider.getSecondaryInstrumentations()).thenReturn(java.util.List.of("acoustic guitar"));
        org.mockito.Mockito.when(mockProvider.getTempoGrooves()).thenReturn(java.util.List.of("fast tempo"));
        org.mockito.Mockito.when(mockProvider.getAcousticEnvironments()).thenReturn(java.util.List.of("reverb room"));

        LyriaSystemPromptConfig customConfig = new LyriaSystemPromptConfig();
        customConfig.setProvider(mockProvider);

        LyriaSystemPromptConfig.MusicVariation variation = customConfig.randomVariation();
        String prompt = customConfig.buildPrompt("jazz", "happy", "piano", variation);

        assertThat(prompt).contains("Custom template:");
        assertThat(prompt).contains("genre=jazz");
        assertThat(prompt).contains("mood=happy");
        assertThat(prompt).contains("instr=piano");
        assertThat(prompt).contains("key=G major");
        assertThat(prompt).contains("sec=acoustic guitar");
        assertThat(prompt).contains("groove=fast tempo");
        assertThat(prompt).contains("env=reverb room");
    }
}
