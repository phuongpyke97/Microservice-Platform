package com.platform.crbtcampaign.service;

import com.platform.common.ai.LyriaPromptProvider;
import com.platform.crbtcampaign.entity.LyriaPromptConfig;
import com.platform.crbtcampaign.repository.LyriaPromptConfigRepository;
import java.util.Collections;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class LyriaPromptProviderImpl implements LyriaPromptProvider {

    private final LyriaPromptConfigRepository repository;

    public LyriaPromptProviderImpl(LyriaPromptConfigRepository repository) {
        this.repository = repository;
    }

    /** Active config for an explicit model. */
    @Cacheable(value = "lyria_prompts", key = "#model")
    public LyriaPromptConfig getActiveConfig(String model) {
        return repository.findFirstByModelAndStatusOrderByVersionDesc(model, "ACTIVE")
                .orElse(null);
    }

    /** Default-model active config (generation entry point — kept deterministic). */
    public LyriaPromptConfig getActiveConfig() {
        return getActiveConfig(LyriaPromptConfig.DEFAULT_MODEL);
    }

    @Override
    public String getTemplate() {
        return getTemplate(LyriaPromptConfig.DEFAULT_MODEL);
    }

    @Override
    public List<String> getKeys() {
        return getKeys(LyriaPromptConfig.DEFAULT_MODEL);
    }

    @Override
    public List<String> getSecondaryInstrumentations() {
        return getSecondaryInstrumentations(LyriaPromptConfig.DEFAULT_MODEL);
    }

    @Override
    public List<String> getTempoGrooves() {
        return getTempoGrooves(LyriaPromptConfig.DEFAULT_MODEL);
    }

    @Override
    public List<String> getAcousticEnvironments() {
        return getAcousticEnvironments(LyriaPromptConfig.DEFAULT_MODEL);
    }

    // Model-aware resolution (G1): generation reads the active prompt for the
    // exact model being invoked, so CMS edits to non-default models take effect.
    @Override
    public String getTemplate(String model) {
        LyriaPromptConfig config = getActiveConfig(resolveModel(model));
        return config != null ? config.getPromptTemplate() : null;
    }

    @Override
    public List<String> getKeys(String model) {
        LyriaPromptConfig config = getActiveConfig(resolveModel(model));
        return config != null ? config.getKeys() : Collections.emptyList();
    }

    @Override
    public List<String> getSecondaryInstrumentations(String model) {
        LyriaPromptConfig config = getActiveConfig(resolveModel(model));
        return config != null ? config.getSecondaryInstrumentations() : Collections.emptyList();
    }

    @Override
    public List<String> getTempoGrooves(String model) {
        LyriaPromptConfig config = getActiveConfig(resolveModel(model));
        return config != null ? config.getTempoGrooves() : Collections.emptyList();
    }

    @Override
    public List<String> getAcousticEnvironments(String model) {
        LyriaPromptConfig config = getActiveConfig(resolveModel(model));
        return config != null ? config.getAcousticEnvironments() : Collections.emptyList();
    }

    private String resolveModel(String model) {
        return (model == null || model.isBlank()) ? LyriaPromptConfig.DEFAULT_MODEL : model;
    }
}
