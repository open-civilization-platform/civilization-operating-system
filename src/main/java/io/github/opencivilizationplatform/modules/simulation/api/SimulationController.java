package io.github.opencivilizationplatform.modules.simulation.api;

import io.github.opencivilizationplatform.modules.simulation.api.dto.SimulationStatusResponse;
import io.github.opencivilizationplatform.modules.simulation.application.CortexEngineService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/simulation")
public class SimulationController {

    private final CortexEngineService cortexEngineService;

    public SimulationController(CortexEngineService cortexEngineService) {
        this.cortexEngineService = cortexEngineService;
    }

    @GetMapping("/status")
    public SimulationStatusResponse getStatus() {
        return cortexEngineService.getStatus();
    }
}
