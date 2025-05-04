package com.joe.task.repo;

import com.joe.task.entity.EnvConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EnvConfigRepository extends JpaRepository<EnvConfig, Long> {
    
    /**
     * Find all visible environments ordered by sequence
     * @return List of visible environments
     */
    @Query("SELECT e FROM EnvConfig e WHERE e.isHidden = false ORDER BY e.sequence ASC")
    List<EnvConfig> findAllVisibleOrderBySequence();
    
    /**
     * Find all environments ordered by sequence
     * @return List of all environments
     */
    @Query("SELECT e FROM EnvConfig e ORDER BY e.sequence ASC")
    List<EnvConfig> findAllOrderBySequence();
} 