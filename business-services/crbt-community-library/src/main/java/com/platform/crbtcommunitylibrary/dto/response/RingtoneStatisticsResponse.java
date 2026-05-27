package com.platform.crbtcommunitylibrary.dto.response;

public record RingtoneStatisticsResponse(
    long totalTracks,
    long activeTracks,
    long inactiveTracks,
    long totalSelections
) {
}
