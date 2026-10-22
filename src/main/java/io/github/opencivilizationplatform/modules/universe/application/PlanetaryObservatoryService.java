package io.github.opencivilizationplatform.modules.universe.application;

import io.github.opencivilizationplatform.modules.universe.domain.PlanetaryTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

@Service
public class PlanetaryObservatoryService {

    private static final Logger log = LoggerFactory.getLogger(PlanetaryObservatoryService.class);

    private final AtomicReference<PlanetaryTelemetry> currentTelemetry =
            new AtomicReference<>(new PlanetaryTelemetry(15.0, 415.0, 12, 95.0));

    public PlanetaryTelemetry getTelemetry() {
        return currentTelemetry.get();
    }

    public void updateTelemetry(double tempDelta, double carbonDelta, int satelliteDelta, double healthDelta) {
        PlanetaryTelemetry prev = currentTelemetry.get();
        double newTemp = Math.max(-50.0, Math.min(60.0, prev.globalTemp() + tempDelta));
        double newCarbon = Math.max(200.0, Math.min(1000.0, prev.atmosphericCarbon() + carbonDelta));
        int newSatellites = Math.max(0, prev.orbitalSatellites() + satelliteDelta);
        double newHealth = Math.max(0.0, Math.min(100.0, prev.biosphereHealthScore() + healthDelta));

        PlanetaryTelemetry next = new PlanetaryTelemetry(newTemp, newCarbon, newSatellites, newHealth);
        currentTelemetry.set(next);
        log.info("[PLANETARY OBSERVATORY] Updated telemetry: Temp={}C, Carbon={}ppm, Satellites={}, BiosphereHealth={}",
                next.globalTemp(), next.atmosphericCarbon(), next.orbitalSatellites(), next.biosphereHealthScore());
    }

    public void processObservatoryTick() {
        updateTelemetry(0.01, 0.05, 0, -0.01);
    }
}
