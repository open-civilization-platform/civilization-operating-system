package io.github.opencivilizationplatform.modules.universe;

import io.github.opencivilizationplatform.modules.universe.application.PlanetaryObservatoryService;
import io.github.opencivilizationplatform.modules.universe.domain.PlanetaryTelemetry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlanetaryObservatoryServiceTest {

    private PlanetaryObservatoryService service;

    @BeforeEach
    void setUp() {
        service = new PlanetaryObservatoryService();
    }

    @Test
    void shouldReturnDefaultTelemetry() {
        PlanetaryTelemetry telemetry = service.getTelemetry();
        assertNotNull(telemetry);
        assertEquals(15.0, telemetry.globalTemp());
        assertEquals(415.0, telemetry.atmosphericCarbon());
        assertEquals(12, telemetry.orbitalSatellites());
        assertEquals(95.0, telemetry.biosphereHealthScore());
    }

    @Test
    void shouldUpdateTelemetryCorrectly() {
        service.updateTelemetry(1.5, 10.0, 3, -5.0);
        PlanetaryTelemetry updated = service.getTelemetry();

        assertEquals(16.5, updated.globalTemp());
        assertEquals(425.0, updated.atmosphericCarbon());
        assertEquals(15, updated.orbitalSatellites());
        assertEquals(90.0, updated.biosphereHealthScore());
    }

    @Test
    void shouldProcessObservatoryTick() {
        PlanetaryTelemetry initial = service.getTelemetry();
        service.processObservatoryTick();
        PlanetaryTelemetry ticked = service.getTelemetry();

        assertTrue(ticked.globalTemp() > initial.globalTemp());
        assertTrue(ticked.atmosphericCarbon() > initial.atmosphericCarbon());
    }
}
