package io.github.opencivilizationplatform.modules.strategy.api;

import io.github.opencivilizationplatform.modules.strategy.application.BalanceService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/strategy")
@CrossOrigin(origins = "*")
public class BalanceController {

    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @GetMapping("/balance")
    public List<Map<String, Object>> getBalance() {
        return balanceService.getBalanceReport();
    }
}
