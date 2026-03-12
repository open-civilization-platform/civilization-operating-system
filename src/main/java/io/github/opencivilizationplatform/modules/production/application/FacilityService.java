package io.github.opencivilizationplatform.modules.production.application;

import io.github.opencivilizationplatform.modules.production.domain.Facility;
import io.github.opencivilizationplatform.modules.production.infrastructure.FacilityRepository;
import org.springframework.stereotype.Service;

@Service
public class FacilityService {
    private final FacilityRepository facilityRepository;

    public FacilityService(FacilityRepository facilityRepository) {
        this.facilityRepository = facilityRepository;
    }

    public java.util.List<Facility> getAllFacilities() {
        return facilityRepository.findAll();
    }

    public Facility saveFacility(Facility facility) {
        return facilityRepository.save(facility);
    }
}
