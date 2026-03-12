package io.github.opencivilizationplatform.modules.strategy.application;

import io.github.opencivilizationplatform.modules.needs.infrastructure.NeedRepository;
import io.github.opencivilizationplatform.modules.resources.infrastructure.ResourceRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BalanceService {

    private final ResourceRepository resourceRepository;
    private final NeedRepository needRepository;

    public BalanceService(ResourceRepository resourceRepository, NeedRepository needRepository) {
        this.resourceRepository = resourceRepository;
        this.needRepository = needRepository;
    }

    public List<Map<String, Object>> getBalanceReport() {
        Map<String, Double> supply = resourceRepository.findAll().stream()
                .collect(Collectors.groupingBy(r -> r.getType(), 
                        Collectors.summingDouble(r -> r.getQuantity())));

        Map<String, Double> demand = needRepository.findAll().stream()
                .collect(Collectors.groupingBy(n -> n.getCategory(), 
                        Collectors.summingDouble(n -> n.getQuantity())));

        return supply.keySet().stream()
                .map(category -> {
                    double s = supply.getOrDefault(category, 0.0);
                    double d = demand.getOrDefault(category, 0.0);
                    Map<String, Object> result = new java.util.HashMap<>();
                    result.put("category", category);
                    result.put("supply", s);
                    result.put("demand", d);
                    result.put("percentageMet", d > 0 ? (s / d) * 100 : 100.0);
                    result.put("status", s >= d ? "STABLE" : "DEFICIT");
                    return result;
                })
                .collect(Collectors.toList());
    }
}
