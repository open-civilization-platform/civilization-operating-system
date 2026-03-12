package io.github.opencivilizationplatform.modules.needs.application;

import io.github.opencivilizationplatform.modules.needs.domain.Need;
import io.github.opencivilizationplatform.modules.needs.infrastructure.NeedRepository;
import org.springframework.stereotype.Service;

@Service
public class NeedService {
    private final NeedRepository needRepository;

    public NeedService(NeedRepository needRepository) {
        this.needRepository = needRepository;
    }

    public java.util.List<Need> getAllNeeds() {
        return needRepository.findAll();
    }

    public java.util.List<Need> getNeedsByRegion(String region) {
        return needRepository.findByRegion(region);
    }

    public Need saveNeed(Need need) {
        return needRepository.save(need);
    }
}
