package com.platform.common.core.config;

import com.platform.common.core.exception.BaseException;
import com.platform.common.core.exception.CommonErrorCode;
import com.platform.common.core.exception.ErrorCode;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
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
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode node = mapper.readTree(body);
                    String parsedCode = null;
                    if (node.has("errorCode")) {
                        parsedCode = node.get("errorCode").asText();
                    } else if (node.has("code")) {
                        parsedCode = node.get("code").asText();
                    }
                    if (parsedCode != null && node.has("message")) {
                        String msg = node.get("message").asText();
                        HttpStatus status = HttpStatus.resolve(response.status());
                        if (status == null) {
                            status = HttpStatus.BAD_REQUEST;
                        }
                        final String finalCode = parsedCode;
                        final HttpStatus finalStatus = status;
                        ErrorCode customCode = new ErrorCode() {
                            @Override public String code() { return finalCode; }
                            @Override public String message() { return msg; }
                            @Override public HttpStatus status() { return finalStatus; }
                        };
                        return new BaseException(customCode, msg);
                    }
                } catch (Exception e) {
                    // ignore and log normally
                }
            }
        } catch (IOException e) {
            // ignore
        }
        log.error("Feign client call failed: method={}, status={}, reason={}, body={}", 
            methodKey, response.status(), response.reason(), body);
        if (response.status() == 403) {
            return new BaseException(CommonErrorCode.COMMON_FORBIDDEN);
        }
        if (response.status() == 401) {
            return new BaseException(CommonErrorCode.COMMON_UNAUTHORIZED);
        }
        return new BaseException(CommonErrorCode.COMMON_DOWNSTREAM_ERROR);
    }
}
