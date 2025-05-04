package com.joe.task.repository;

import com.joe.task.entity.AdoInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdoInfoRepository extends JpaRepository<AdoInfo, Long> {
    /**
     * Find repositories by name with pagination (case-insensitive)
     * @param repoName repository name to search for
     * @param pageable pagination information
     * @return Page of matching AdoInfo records
     */
    Page<AdoInfo> findByRepoNameContainingIgnoreCase(String repoName, Pageable pageable);
} 