package io.github.opencivilizationplatform.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalEventMigrationTest {

    @Test
    void migrationCreatesTheGlobalEventsTableUsedByTheEntity() throws Exception {
        String migration;
        try (var input = new ClassPathResource("db/migration/V25__global_events.sql").getInputStream()) {
            migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(migration.matches("(?is).*CREATE\\s+TABLE\\s+global_events\\s*\\(.*"));
        assertTrue(migration.contains("affected_civ_ids"));
        assertTrue(migration.contains("ticks_remaining"));
    }
}