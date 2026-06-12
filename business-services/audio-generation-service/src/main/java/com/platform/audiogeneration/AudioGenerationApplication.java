package com.platform.audiogeneration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = {"com.platform.audiogeneration", "com.platform.common"})
@EnableDiscoveryClient
@EnableFeignClients
@EnableAsync
@org.springframework.scheduling.annotation.EnableScheduling
public class AudioGenerationApplication {
    public static void main(String[] args) {
        SpringApplication.run(AudioGenerationApplication.class, args);
    }
}
