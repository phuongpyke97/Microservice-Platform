package com.platform.fileservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.platform.fileservice", "com.platform.common"})
@org.springframework.scheduling.annotation.EnableScheduling
@org.springframework.cloud.openfeign.EnableFeignClients
public class FileServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(FileServiceApplication.class, args);
    }
}
