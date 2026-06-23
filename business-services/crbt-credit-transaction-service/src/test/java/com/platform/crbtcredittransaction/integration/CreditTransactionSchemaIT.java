package com.platform.crbtcredittransaction.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.crbtcredittransaction.entity.CreditTransaction;
import com.platform.crbtcredittransaction.repository.CreditTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Live Testcontainers check: runs the real Flyway migrations (V1..V3) against a
 * throwaway PostgreSQL, then lets Hibernate {@code ddl-auto=validate} confirm the
 * {@link CreditTransaction} entity matches the migrated schema. A round-trip also
 * proves the V3 balance/model columns are writable and readable.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CreditTransactionSchemaIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        // Keep the JPA slice offline — no config server / discovery in tests.
        registry.add("spring.cloud.config.enabled", () -> "false");
        registry.add("spring.cloud.config.import-check.enabled", () -> "false");
        registry.add("spring.cloud.discovery.enabled", () -> "false");
        registry.add("eureka.client.enabled", () -> "false");
    }

    @Autowired
    private CreditTransactionRepository repository;

    @Test
    void migrationsMatchEntity_andV3BalanceColumnsPersist() {
        CreditTransaction tx = new CreditTransaction(
            42L, 1, "DEBIT", "AI Music IT", "MUSIC-IT-1", System.currentTimeMillis(),
            false, "AI", 10, 9, "lyria-3-pro-preview");

        CreditTransaction saved = repository.save(tx);

        CreditTransaction found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getBeforeBalance()).isEqualTo(10);
        assertThat(found.getAfterBalance()).isEqualTo(9);
        assertThat(found.getModel()).isEqualTo("lyria-3-pro-preview");
    }
}
