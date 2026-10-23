package io.github.opencivilizationplatform.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TreatyMigrationTest {

    @Test
    void migrationCreatesTheTreatyTableUsedByTheEntity() throws Exception {
        String migration;
        try (var input = new ClassPathResource("db/migration/V27__treaties.sql").getInputStream()) {
            migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(migration.matches("(?is).*CREATE\\s+TABLE\\s+treaties\\s*\\(.*"));
        assertTrue(migration.contains("proposer_civ_id"));
        assertTrue(migration.contains("invited_civ_ids"));
        assertTrue(migration.contains("signatory_civ_ids"));
    }
}