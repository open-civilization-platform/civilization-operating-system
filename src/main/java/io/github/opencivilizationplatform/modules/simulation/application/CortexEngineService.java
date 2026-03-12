package io.github.opencivilizationplatform.modules.simulation.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.github.opencivilizationplatform.core.event.BiosphereCriticalEvent;
import io.github.opencivilizationplatform.modules.participation.application.RuleService;
import io.github.opencivilizationplatform.modules.participation.domain.Rule;
import io.github.opencivilizationplatform.modules.simulation.api.dto.SimulationStatusResponse;
import io.github.opencivilizationplatform.modules.strategy.application.BalanceService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class CortexEngineService {

    private final RuleService ruleService;
    private final BalanceService balanceService;
    private final ObjectMapper objectMapper;

    private final AtomicReference<String> lastDecision = new AtomicReference<>("Initializing Civilization Cortex...");
    private final AtomicInteger activeRulesCount = new AtomicInteger(0);
    private final List<String> monitoredCategories = new ArrayList<>();

    public CortexEngineService(RuleService ruleService, BalanceService balanceService, ObjectMapper objectMapper) {
        this.ruleService = ruleService;
        this.balanceService = balanceService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedRate = 15000)
    public void runSimulationCycle() {
        System.out.println("[CORTEX SIMULATION CYCLE: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "]");
        
        List<Rule> rules = ruleService.getValidatedRules();
        if (rules == null || rules.isEmpty()) return;
        activeRulesCount.set(rules.size());
        
        List<Map<String, Object>> balance = balanceService.getBalanceReport();
        
        synchronized (monitoredCategories) {
            monitoredCategories.clear();
            balance.forEach(b -> monitoredCategories.add((String) b.get("category")));
        }

        for (Rule rule : rules) {
            try {
                JsonNode logic = objectMapper.readTree(rule.getLogicCode());
                JsonNode typeNode = logic.get("type");
                if (typeNode == null) continue;
                
                String type = typeNode.asString();

                if ("RESERVE_CHECK".equals(type)) {
                    JsonNode metricNode = logic.get("metric");
                    if (metricNode == null) continue;
                    
                    String metricCat = metricNode.asString();
                    balance.stream()
                        .filter(b -> metricCat.equals(b.get("category")))
                        .findFirst()
                        .ifPresent(b -> {
                            double percentage = (double) b.get("percentageMet");
                            if (percentage < 100) {
                                String decision = String.format("DECISION: %s deficiency detected (%.1f%%). Triggering Rule '%s'. Resource reallocation initialized.", 
                                    metricCat, percentage, rule.getTitle());
                                lastDecision.set(decision);
                                System.out.println(decision);
                            }
                        });
                }
            } catch (Exception e) {
                System.err.println("Error evaluating rule " + rule.getId() + ": " + e.getMessage());
            }
        }
    }

    @EventListener
    public void onBiosphereCritical(BiosphereCriticalEvent event) {
        String decision = String.format("AUTO-REACTION: Biosphere Redline! Target: %s. Value: %.2f. Triggering emergency preservation protocols.", 
            event.getMetric().getName(), event.getMetric().getValue());
        lastDecision.set(decision);
        System.out.println(decision);
    }

    public SimulationStatusResponse getStatus() {
        List<String> categories;
        synchronized (monitoredCategories) {
            categories = new ArrayList<>(monitoredCategories);
        }
        return new SimulationStatusResponse(
            "Cortex Simulation Engine (Java Native)",
            activeRulesCount.get(),
            lastDecision.get(),
            categories
        );
    }
}
