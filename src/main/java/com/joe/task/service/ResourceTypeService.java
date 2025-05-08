package com.joe.task.service;

import com.joe.task.entity.ResourceType;
import com.joe.task.repository.ResourceTypeRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ResourceTypeService {

    private final ResourceTypeRepository resourceTypeRepository;

    @Autowired
    public ResourceTypeService(ResourceTypeRepository resourceTypeRepository) {
        this.resourceTypeRepository = resourceTypeRepository;
    }

    @PostConstruct
    public void init() {
        // Initialize default data if empty
        if (resourceTypeRepository.count() == 0) {
            ResourceType k8s = new ResourceType();
            k8s.setResourceName("K8S");
            k8s.setSequence(1);
            resourceTypeRepository.save(k8s);

            ResourceType hasura = new ResourceType();
            hasura.setResourceName("Hasura");
            hasura.setSequence(2);
            resourceTypeRepository.save(hasura);
        }
    }

    @Cacheable(value = "resourceTypeCache", key = "'allResourceTypes'")
    public List<ResourceType> getAllResourceTypes() {
        return resourceTypeRepository.findAll();
    }

    @Cacheable(value = "resourceTypeCache", key = "#id")
    public Optional<ResourceType> getResourceTypeById(Long id) {
        return resourceTypeRepository.findById(id);
    }

    @CacheEvict(value = "resourceTypeCache", allEntries = true)
    public ResourceType saveResourceType(ResourceType resourceType) {
        return resourceTypeRepository.save(resourceType);
    }

    @CacheEvict(value = "resourceTypeCache", allEntries = true)
    public void deleteResourceType(Long id) {
        resourceTypeRepository.deleteById(id);
    }
} 