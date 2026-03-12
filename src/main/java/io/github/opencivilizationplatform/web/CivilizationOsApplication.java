package io.github.opencivilizationplatform.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"io.github.opencivilizationplatform"})
@EnableScheduling
public class CivilizationOsApplication {

    public static void main(String[] args) {
        SpringApplication.run(CivilizationOsApplication.class, args);
    }
}
