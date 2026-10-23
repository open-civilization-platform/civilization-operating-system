package io.github.opencivilizationplatform.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TechnologyMigrationTest {

    @Test
    void migrationAddsTechnologyBonusFieldsUsedByTheEntity() throws Exception {
        String migration;
        try (var input = new ClassPathResource("db/migration/V28__technology_bonus_fields.sql").getInputStream()) {
            migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(migration.contains("resource_bonus"));
        assertTrue(migration.contains("bonus_amount"));
    }
}