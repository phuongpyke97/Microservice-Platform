package com.platform.crbtcommunitylibrary.util;

public record AudioAnalysisResult(
    int durationSeconds,
    long sizeBytes,
    boolean hasVocal
) {
}
