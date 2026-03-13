package io.github.opencivilizationplatform.web.controller;

import io.github.opencivilizationplatform.modules.contribution.application.ContributionService;
import io.github.opencivilizationplatform.modules.execution.infrastructure.AutomationUnitRepository;
import io.github.opencivilizationplatform.modules.logistics.application.ShipmentService;
import io.github.opencivilizationplatform.modules.monitoring.application.BiosphereMetricService;
import io.github.opencivilizationplatform.modules.needs.application.NeedService;
import io.github.opencivilizationplatform.modules.participation.application.InteractionService;
import io.github.opencivilizationplatform.modules.participation.application.RuleService;
import io.github.opencivilizationplatform.modules.production.application.FacilityService;
import io.github.opencivilizationplatform.modules.resources.application.ResourceService;
import io.github.opencivilizationplatform.modules.simulation.application.CortexEngineService;
import io.github.opencivilizationplatform.modules.social.application.SocialStabilityService;
import io.github.opencivilizationplatform.modules.strategy.application.BalanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class PageControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean private BiosphereMetricService biosphereMetricService;
    @MockitoBean private ResourceService resourceService;
    @MockitoBean private NeedService needService;
    @MockitoBean private CortexEngineService cortexEngineService;
    @MockitoBean private RuleService ruleService;
    @MockitoBean private InteractionService interactionService;
    @MockitoBean private FacilityService facilityService;
    @MockitoBean private ShipmentService shipmentService;
    @MockitoBean private ContributionService contributionService;
    @MockitoBean private SocialStabilityService socialStabilityService;
    @MockitoBean private BalanceService balanceService;
    @MockitoBean private AutomationUnitRepository automationRepository;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void testDashboard() throws Exception {
        when(resourceService.getAllResources()).thenReturn(new ArrayList<>());
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("viewName", "dashboard"));
    }

    @Test
    void testBiosphere() throws Exception {
        when(biosphereMetricService.getAllMetrics()).thenReturn(new ArrayList<>());
        mockMvc.perform(get("/biosphere"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("viewName", "biosphere"));
    }

    @Test
    void testResources() throws Exception {
        when(resourceService.getAllResources()).thenReturn(new ArrayList<>());
        mockMvc.perform(get("/resources"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("viewName", "resources"));
    }

    @Test
    void testNeeds() throws Exception {
        when(needService.getAllNeeds()).thenReturn(new ArrayList<>());
        mockMvc.perform(get("/needs"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("viewName", "needs"));
    }

    @Test
    void testStrategy() throws Exception {
        mockMvc.perform(get("/strategy"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("viewName", "strategy"));
    }

    @Test
    void testProduction() throws Exception {
        when(facilityService.getAllFacilities()).thenReturn(new ArrayList<>());
        mockMvc.perform(get("/production"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("viewName", "production"));
    }

    @Test
    void testLogistics() throws Exception {
        when(shipmentService.getAllShipments()).thenReturn(new ArrayList<>());
        mockMvc.perform(get("/logistics"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("viewName", "logistics"));
    }

    @Test
    void testInteraction() throws Exception {
        when(interactionService.getAllInteractions()).thenReturn(new ArrayList<>());
        mockMvc.perform(get("/interaction"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("viewName", "interaction"));
    }

    @Test
    void testConstitution() throws Exception {
        when(ruleService.getAllRules()).thenReturn(new ArrayList<>());
        mockMvc.perform(get("/constitution"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("viewName", "constitution"));
    }

    @Test
    void testPurpose() throws Exception {
        mockMvc.perform(get("/purpose"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("viewName", "purpose"));
    }

    @Test
    void testSocial() throws Exception {
        mockMvc.perform(get("/social"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("viewName", "social"));
    }

    @Test
    void testSimulation() throws Exception {
        when(automationRepository.findAll()).thenReturn(new ArrayList<>());
        mockMvc.perform(get("/simulation"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("viewName", "simulation"));
    }
}
