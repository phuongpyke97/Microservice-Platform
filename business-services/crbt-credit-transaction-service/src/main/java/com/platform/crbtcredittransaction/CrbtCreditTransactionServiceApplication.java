package com.platform.crbtcredittransaction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.platform.crbtcredittransaction", "com.platform.common"})
public class CrbtCreditTransactionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrbtCreditTransactionServiceApplication.class, args);
    }
}
