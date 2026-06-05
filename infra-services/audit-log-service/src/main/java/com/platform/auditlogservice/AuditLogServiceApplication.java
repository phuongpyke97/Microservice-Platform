package com.platform.auditlogservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.platform.auditlogservice", "com.platform.common"})
@EnableScheduling
public class AuditLogServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditLogServiceApplication.class, args);
    }
}
