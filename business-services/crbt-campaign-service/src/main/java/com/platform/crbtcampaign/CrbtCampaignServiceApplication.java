package com.platform.crbtcampaign;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.platform.crbtcampaign", "com.platform.common"})
@EnableFeignClients(basePackages = "com.platform.crbtcampaign.client")
@EnableScheduling
@EnableCaching
public class CrbtCampaignServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrbtCampaignServiceApplication.class, args);
    }
}
