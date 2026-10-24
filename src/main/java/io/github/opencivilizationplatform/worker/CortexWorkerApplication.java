package io.github.opencivilizationplatform.worker;

import io.github.opencivilizationplatform.config.JwtAuthFilter;
import io.github.opencivilizationplatform.config.JwtService;
import io.github.opencivilizationplatform.config.KafkaConfig;
import io.github.opencivilizationplatform.config.KafkaConsumerConfig;
import io.github.opencivilizationplatform.config.SecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@Profile("worker")
@SpringBootApplication(scanBasePackages = {
    "io.github.opencivilizationplatform.worker",
    "io.github.opencivilizationplatform.core.eventbus",
    "io.github.opencivilizationplatform.modules.cortex.cortex",
    "io.github.opencivilizationplatform.modules.civilization.infrastructure",
    "io.github.opencivilizationplatform.modules.region.infrastructure"
})
@Import({
    KafkaConfig.class,
    KafkaConsumerConfig.class,
    SecurityConfig.class,
    JwtAuthFilter.class,
    JwtService.class
})
@EnableScheduling
@EntityScan(basePackages = {
    "io.github.opencivilizationplatform.modules.civilization.domain",
    "io.github.opencivilizationplatform.modules.region.domain",
    "io.github.opencivilizationplatform.modules.cortex.domain"
})
@EnableJpaRepositories(basePackages = {
    "io.github.opencivilizationplatform.modules.civilization.infrastructure",
    "io.github.opencivilizationplatform.modules.region.infrastructure"
})
public class CortexWorkerApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(CortexWorkerApplication.class, args);
    }
}
