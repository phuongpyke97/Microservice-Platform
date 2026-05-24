package com.platform.creditwallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.platform.creditwallet", "com.platform.common"})
public class CreditWalletApplication {

    public static void main(String[] args) {
        SpringApplication.run(CreditWalletApplication.class, args);
    }
}
