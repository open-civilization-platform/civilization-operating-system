package io.github.opencivilizationplatform.modules.social.application;

import io.github.opencivilizationplatform.modules.social.domain.Case;
import io.github.opencivilizationplatform.modules.social.domain.Incident;
import io.github.opencivilizationplatform.modules.social.infrastructure.CaseRepository;
import io.github.opencivilizationplatform.modules.social.infrastructure.IncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SocialStabilityServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private CaseRepository caseRepository;

    @InjectMocks
    private SocialStabilityService socialStabilityService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetAllIncidents() {
        when(incidentRepository.findAll()).thenReturn(Arrays.asList(new Incident(), new Incident()));
        List<Incident> result = socialStabilityService.getAllIncidents();
        assertEquals(2, result.size());
    }

    @Test
    void testGetAllCases() {
        when(caseRepository.findAll()).thenReturn(Arrays.asList(new Case()));
        List<Case> result = socialStabilityService.getAllCases();
        assertEquals(1, result.size());
    }

    @Test
    void testSaveIncident() {
        Incident i = new Incident();
        i.setDescription("Resource Distribution Lag");
        when(incidentRepository.save(i)).thenReturn(i);
        Incident saved = socialStabilityService.reportIncident(i);
        assertNotNull(saved);
        assertEquals("Resource Distribution Lag", saved.getDescription());
    }
}
