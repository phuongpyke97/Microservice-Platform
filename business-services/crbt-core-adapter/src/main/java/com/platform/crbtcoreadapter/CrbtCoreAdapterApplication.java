package com.platform.crbtcoreadapter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = {"com.platform.crbtcoreadapter", "com.platform.common"})
@EnableDiscoveryClient
@EnableFeignClients
@EnableAsync
public class CrbtCoreAdapterApplication {
    public static void main(String[] args) {
        SpringApplication.run(CrbtCoreAdapterApplication.class, args);
    }
}
