package io.github.opencivilizationplatform.modules.simulation.api.dto;

import java.util.List;

public class SimulationStatusResponse {
    private String engine;
    private int activeRules;
    private String lastDecision;
    private List<String> monitoredCategories;

    public SimulationStatusResponse(String engine, int activeRules, String lastDecision, List<String> monitoredCategories) {
        this.engine = engine;
        this.activeRules = activeRules;
        this.lastDecision = lastDecision;
        this.monitoredCategories = monitoredCategories;
    }

    public String getEngine() { return engine; }
    public void setEngine(String engine) { this.engine = engine; }

    public int getActiveRules() { return activeRules; }
    public void setActiveRules(int activeRules) { this.activeRules = activeRules; }

    public String getLastDecision() { return lastDecision; }
    public void setLastDecision(String lastDecision) { this.lastDecision = lastDecision; }

    public List<String> getMonitoredCategories() { return monitoredCategories; }
    public void setMonitoredCategories(List<String> monitoredCategories) { this.monitoredCategories = monitoredCategories; }
}
