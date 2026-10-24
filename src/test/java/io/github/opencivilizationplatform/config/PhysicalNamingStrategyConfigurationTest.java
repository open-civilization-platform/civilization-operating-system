package io.github.opencivilizationplatform.config;

import org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl;
import org.hibernate.boot.model.naming.Identifier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhysicalNamingStrategyConfigurationTest {

    @Test
    void applicationMapsCamelCasePropertiesToMigrationColumnNames() throws Exception {
        var properties = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .get(0);

        assertEquals(
                PhysicalNamingStrategySnakeCaseImpl.class.getName(),
                properties.getProperty("spring.jpa.hibernate.naming.physical-strategy"));

        var strategy = new PhysicalNamingStrategySnakeCaseImpl();
        assertEquals("citizen_id", strategy.toPhysicalColumnName(Identifier.toIdentifier("citizenId"), null).getText());
        assertEquals("current_task", strategy.toPhysicalColumnName(Identifier.toIdentifier("currentTask"), null).getText());
    }
}