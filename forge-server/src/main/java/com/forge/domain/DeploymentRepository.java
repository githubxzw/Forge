package com.forge.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeploymentRepository extends JpaRepository<Deployment, String> {
    Page<Deployment> findByOrderByCreatedAtDesc(Pageable pageable);
    Page<Deployment> findByProjectIdOrderByCreatedAtDesc(String projectId, Pageable pageable);
    Page<Deployment> findByProjectIdAndEnvironmentIdOrderByCreatedAtDesc(String projectId, String environmentId, Pageable pageable);
    List<Deployment> findTop10ByOrderByCreatedAtDesc();

    /** 目标部署之前（不含）同项目同环境的最近一次成功部署 —— 回滚即部署它 */
    Deployment findFirstByProjectIdAndEnvironmentIdAndStatusAndCreatedAtBeforeOrderByCreatedAtDesc(
            String projectId, String environmentId, TaskStatus status, java.time.LocalDateTime before);

    List<Deployment> findByStatusIn(List<TaskStatus> statuses);
}
