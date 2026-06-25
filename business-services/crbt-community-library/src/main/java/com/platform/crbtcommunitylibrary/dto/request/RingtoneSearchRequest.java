package com.platform.crbtcommunitylibrary.dto.request;

public record RingtoneSearchRequest(
    String q,
    Long categoryId,
    Long moodId,
    Boolean status,
    String createdFrom,
    String createdTo,
    Long selectionCountFrom,
    Long selectionCountTo,
    String postBy
) {
}
