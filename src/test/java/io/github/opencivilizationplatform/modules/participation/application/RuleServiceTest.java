package io.github.opencivilizationplatform.modules.participation.application;

import io.github.opencivilizationplatform.modules.participation.domain.Rule;
import io.github.opencivilizationplatform.modules.participation.infrastructure.RuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RuleServiceTest {

    @Mock
    private RuleRepository ruleRepository;

    @InjectMocks
    private RuleService ruleService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetAllRules() {
        Rule rule1 = new Rule();
        Rule rule2 = new Rule();
        when(ruleRepository.findAll()).thenReturn(List.of(rule1, rule2));

        List<Rule> result = ruleService.getAllRules();

        assertEquals(2, result.size());
        verify(ruleRepository, times(1)).findAll();
    }

    @Test
    void testGetValidatedRules() {
        Rule activeValidated = new Rule();
        activeValidated.setStatus("ACTIVE");
        activeValidated.setValidationStatus("SCIENTIFICALLY_VALIDATED");

        Rule inactive = new Rule();
        inactive.setStatus("INACTIVE");
        inactive.setValidationStatus("SCIENTIFICALLY_VALIDATED");

        Rule unvalidated = new Rule();
        unvalidated.setStatus("ACTIVE");
        unvalidated.setValidationStatus("PENDING");

        List<Rule> allRules = List.of(activeValidated, inactive, unvalidated);
        when(ruleRepository.findAll()).thenReturn(allRules);

        List<Rule> result = ruleService.getValidatedRules();

        assertEquals(1, result.size());
        assertEquals("ACTIVE", result.get(0).getStatus());
        assertEquals("SCIENTIFICALLY_VALIDATED", result.get(0).getValidationStatus());
    }

    @Test
    void testSaveRule() {
        Rule rule = new Rule();
        rule.setTitle("Ocean Preservation Rule");
        when(ruleRepository.save(any(Rule.class))).thenReturn(rule);

        Rule saved = ruleService.saveRule(rule);

        assertNotNull(saved);
        assertEquals("Ocean Preservation Rule", saved.getTitle());
    }
}
