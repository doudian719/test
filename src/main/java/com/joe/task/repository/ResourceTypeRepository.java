package com.joe.task.repository;

import com.joe.task.entity.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResourceTypeRepository extends JpaRepository<ResourceType, Long> {

}