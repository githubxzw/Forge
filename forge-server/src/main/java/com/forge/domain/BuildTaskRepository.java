package com.forge.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BuildTaskRepository extends JpaRepository<BuildTask, String> {
    Page<BuildTask> findByProjectIdOrderByCreatedAtDesc(String projectId, Pageable pageable);
    Page<BuildTask> findByOrderByCreatedAtDesc(Pageable pageable);
    boolean existsByProjectIdAndStatusIn(String projectId, List<TaskStatus> statuses);
    List<BuildTask> findByStatusIn(List<TaskStatus> statuses);
}
