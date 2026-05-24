package com.platform.crbtcommunitylibrary;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication(scanBasePackages = {"com.platform.crbtcommunitylibrary", "com.platform.common"})
@EnableCaching
public class CrbtCommunityLibraryApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrbtCommunityLibraryApplication.class, args);
    }
}
