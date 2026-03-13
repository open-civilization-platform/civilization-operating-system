package io.github.opencivilizationplatform.modules.production.application;

import io.github.opencivilizationplatform.modules.production.domain.Facility;
import io.github.opencivilizationplatform.modules.production.infrastructure.FacilityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FacilityServiceTest {

    @Mock
    private FacilityRepository facilityRepository;

    @InjectMocks
    private FacilityService facilityService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetAllFacilities() {
        Facility f1 = new Facility();
        Facility f2 = new Facility();
        when(facilityRepository.findAll()).thenReturn(Arrays.asList(f1, f2));

        List<Facility> result = facilityService.getAllFacilities();

        assertEquals(2, result.size());
        verify(facilityRepository, times(1)).findAll();
    }

    @Test
    void testSaveFacility() {
        Facility f = new Facility();
        f.setName("Automated Vertical Farm Alpha");
        when(facilityRepository.save(any(Facility.class))).thenReturn(f);

        Facility saved = facilityService.saveFacility(f);

        assertNotNull(saved);
        assertEquals("Automated Vertical Farm Alpha", saved.getName());
    }
}
