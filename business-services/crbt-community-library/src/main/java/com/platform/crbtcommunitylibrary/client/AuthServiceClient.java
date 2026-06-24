package com.platform.crbtcommunitylibrary.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.List;
import java.util.Map;

@FeignClient(name = "auth-service")
public interface AuthServiceClient {

    @PostMapping("/internal/crbt/users/msisdns")
    Map<Long, String> getMsisdnsByUserIds(@RequestBody List<Long> userIds);
}
