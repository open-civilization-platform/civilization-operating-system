package io.github.opencivilizationplatform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper toolsObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public com.fasterxml.jackson.databind.ObjectMapper legacyObjectMapper() {
        return new com.fasterxml.jackson.databind.ObjectMapper();
    }
}
