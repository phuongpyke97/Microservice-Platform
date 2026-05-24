package com.platform.fileservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ConfirmFileRequest(@NotBlank String targetBucket) {
}
