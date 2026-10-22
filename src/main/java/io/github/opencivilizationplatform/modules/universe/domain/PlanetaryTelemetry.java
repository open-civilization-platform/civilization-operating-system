package io.github.opencivilizationplatform.modules.universe.domain;

public record PlanetaryTelemetry(
        double globalTemp,
        double atmosphericCarbon,
        int orbitalSatellites,
        double biosphereHealthScore
) {
}
