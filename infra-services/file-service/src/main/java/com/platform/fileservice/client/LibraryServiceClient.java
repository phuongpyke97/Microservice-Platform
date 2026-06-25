package com.platform.fileservice.client;

import com.platform.common.core.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "crbt-community-library")
public interface LibraryServiceClient {

    @PostMapping("/library/ringtones/approve-diy")
    ApiResponse<java.util.Map<String, Object>> approveDiyTone(@RequestBody ApproveDiyToneRequest request);

    record ApproveDiyToneRequest(
        Long fileId,
        String title,
        String artistName,
        String coverImageUrl,
        Boolean featured,
        Boolean status,
        Long categoryId,
        Long moodId
    ) {}
}
