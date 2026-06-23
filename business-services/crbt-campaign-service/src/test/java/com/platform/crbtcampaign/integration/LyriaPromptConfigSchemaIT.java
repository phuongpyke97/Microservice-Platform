package com.platform.crbtcampaign.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.crbtcampaign.entity.LyriaPromptConfig;
import com.platform.crbtcampaign.repository.LyriaPromptConfigRepository;
import java.util.List;
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
 * Live Testcontainers check: runs the real Flyway migrations (V1..V7) against a
 * throwaway PostgreSQL, then lets Hibernate {@code ddl-auto=validate} confirm the
 * {@link LyriaPromptConfig} entity matches the migrated schema (V6 create + V7
 * model/version columns). Round-trip proves the per-model versioning columns
 * persist and the V7-seeded pro-preview row is queryable.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class LyriaPromptConfigSchemaIT {

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
    private LyriaPromptConfigRepository repository;

    @Test
    void v7SeededProPreviewRowIsActive() {
        // Seeded by V7 migration — confirms the migration ran and per-model query works.
        LyriaPromptConfig pro = repository
            .findFirstByModelAndStatusOrderByVersionDesc("lyria-3-pro-preview", "ACTIVE")
            .orElseThrow();

        assertThat(pro.getVersion()).isEqualTo(1);
        assertThat(pro.getPromptTemplate()).contains("Lyria 3 Pro");
        assertThat(pro.getKeys()).isNotEmpty();
    }

    @Test
    void newVersionRoundTripsThroughModelVersioningColumns() {
        LyriaPromptConfig v2 = new LyriaPromptConfig(
            "lyria-3-pro-preview", 2,
            "IT template: genre=%s mood=%s instr=%s bpm=%d key=%s sec=%s groove=%s env=%s",
            List.of("C major"), List.of("grand piano"), List.of("slow groove"), List.of("studio vibe"),
            "INACTIVE", "it-admin");

        LyriaPromptConfig saved = repository.save(v2);

        LyriaPromptConfig found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getModel()).isEqualTo("lyria-3-pro-preview");
        assertThat(found.getVersion()).isEqualTo(2);
        assertThat(found.getCreatedBy()).isEqualTo("it-admin");
        assertThat(found.getKeys()).containsExactly("C major");
    }
}
