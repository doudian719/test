package com.joe.task.repo;

import com.joe.task.entity.JobLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobLogRepository extends JpaRepository<JobLog, Long> {

}
