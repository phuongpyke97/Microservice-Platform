package com.platform.auth.config;

import com.platform.auth.entity.User;
import com.platform.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private static final String ADMIN_EMAIL = "admin@platform.local";
    private static final String ADMIN_PASSWORD = "Elcom@123";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(ADMIN_EMAIL)) {
            return;
        }
        var admin = new User(
                null,
                ADMIN_EMAIL,
                passwordEncoder.encode(ADMIN_PASSWORD),
                Set.of("ROLE_ADMIN")
        );
        userRepository.save(admin);
        log.info("Admin user created: {}", ADMIN_EMAIL);
    }
}
