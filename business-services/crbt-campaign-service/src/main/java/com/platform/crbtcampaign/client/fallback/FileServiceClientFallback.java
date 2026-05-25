package com.platform.crbtcampaign.client.fallback;

import com.platform.common.core.exception.BaseException;
import com.platform.crbtcampaign.client.FileServiceClient;
import com.platform.crbtcampaign.exception.CampaignErrorCode;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class FileServiceClientFallback implements FallbackFactory<FileServiceClient> {

    @Override
    public FileServiceClient create(Throwable cause) {
        return (bytes, bucket) -> {
            throw new BaseException(CampaignErrorCode.CAMPAIGN_FILE_UPLOAD_FAILED);
        };
    }
}
