package io.github.opencivilizationplatform.modules.resources.application;

import io.github.opencivilizationplatform.modules.resources.domain.Resource;
import io.github.opencivilizationplatform.modules.resources.infrastructure.ResourceRepository;
import org.springframework.stereotype.Service;

@Service
public class ResourceService {
    private final ResourceRepository resourceRepository;

    public ResourceService(ResourceRepository resourceRepository) {
        this.resourceRepository = resourceRepository;
    }

    public java.util.List<Resource> getAllResources() {
        return resourceRepository.findAll();
    }

    public Resource saveResource(Resource resource) {
        return resourceRepository.save(resource);
    }
}
