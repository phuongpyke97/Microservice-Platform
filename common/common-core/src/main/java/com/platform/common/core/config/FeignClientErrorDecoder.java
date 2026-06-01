package com.platform.common.core.config;

import com.platform.common.core.exception.BaseException;
import com.platform.common.core.exception.CommonErrorCode;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class FeignClientErrorDecoder implements ErrorDecoder {
    private static final Logger log = LoggerFactory.getLogger(FeignClientErrorDecoder.class);

    @Override
    public Exception decode(String methodKey, Response response) {
        String body = "";
        try {
            if (response.body() != null) {
                body = feign.Util.toString(response.body().asReader(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            // ignore
        }
        log.error("Feign client call failed: method={}, status={}, reason={}, body={}", 
            methodKey, response.status(), response.reason(), body);
        return new BaseException(CommonErrorCode.COMMON_DOWNSTREAM_ERROR);
    }
}
