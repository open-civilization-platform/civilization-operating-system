package io.github.opencivilizationplatform.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentMigrationTest {

    @Test
    void migrationAddsIncidentDispatchAndSeverityFields() throws Exception {
        String migration;
        try (var input = new ClassPathResource("db/migration/V26__incident_dispatch_fields.sql").getInputStream()) {
            migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(migration.contains("assigned_eco_bots"));
        assertTrue(migration.contains("assigned_security_bots"));
        assertTrue(migration.contains("severity"));
    }
}