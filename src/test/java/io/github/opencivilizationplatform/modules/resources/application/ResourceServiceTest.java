package io.github.opencivilizationplatform.modules.resources.application;

import io.github.opencivilizationplatform.modules.resources.domain.Resource;
import io.github.opencivilizationplatform.modules.resources.infrastructure.ResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResourceServiceTest {

    @Mock
    private ResourceRepository resourceRepository;

    @InjectMocks
    private ResourceService resourceService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetAllResources() {
        Resource res1 = new Resource();
        Resource res2 = new Resource();
        when(resourceRepository.findAll()).thenReturn(Arrays.asList(res1, res2));

        List<Resource> result = resourceService.getAllResources();

        assertEquals(2, result.size());
        verify(resourceRepository, times(1)).findAll();
    }

    @Test
    void testSaveResource() {
        Resource res = new Resource();
        res.setName("Iron Ore");
        when(resourceRepository.save(any(Resource.class))).thenReturn(res);

        Resource saved = resourceService.saveResource(res);

        assertNotNull(saved);
        assertEquals("Iron Ore", saved.getName());
        verify(resourceRepository, times(1)).save(res);
    }
}
