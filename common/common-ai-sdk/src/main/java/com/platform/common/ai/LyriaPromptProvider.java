package com.platform.common.ai;

import java.util.List;

public interface LyriaPromptProvider {
    String getTemplate();
    List<String> getKeys();
    List<String> getSecondaryInstrumentations();
    List<String> getTempoGrooves();
    List<String> getAcousticEnvironments();

    // Model-aware overloads (G1): generation resolves the prompt for the model
    // that is actually being called. Default to the no-arg (default-model)
    // behaviour so existing providers/tests keep working.
    default String getTemplate(String model) { return getTemplate(); }
    default List<String> getKeys(String model) { return getKeys(); }
    default List<String> getSecondaryInstrumentations(String model) { return getSecondaryInstrumentations(); }
    default List<String> getTempoGrooves(String model) { return getTempoGrooves(); }
    default List<String> getAcousticEnvironments(String model) { return getAcousticEnvironments(); }
}
