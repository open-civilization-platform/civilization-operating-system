package io.github.opencivilizationplatform.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ElectionMigrationTest {

    @Test
    void migrationCreatesElectionTablesUsedByTheEntities() throws Exception {
        String migration;
        try (var input = new ClassPathResource("db/migration/V24__elections.sql").getInputStream()) {
            migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(migration.matches("(?is).*CREATE\\s+TABLE\\s+elections\\s*\\(.*"));
        assertTrue(migration.matches("(?is).*CREATE\\s+TABLE\\s+election_votes\\s*\\(.*"));
        assertTrue(migration.contains("civilization_id"));
        assertTrue(migration.contains("election_id"));
    }
}