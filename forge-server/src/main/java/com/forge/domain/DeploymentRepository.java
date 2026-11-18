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

    /** 目标部署之前（或同秒更早落库、不含自身）同项目同环境的最近一次成功部署 —— 回滚即部署它 */
    Deployment findFirstByProjectIdAndEnvironmentIdAndStatusAndIdNotAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
            String projectId, String environmentId, TaskStatus status, String id, java.time.LocalDateTime atOrBefore);

    List<Deployment> findByStatusIn(List<TaskStatus> statuses);
    List<Deployment> findByProjectId(String projectId);
    List<Deployment> findByCreatedAtAfter(java.time.LocalDateTime time);
    /** 某项目在某环境的最近一次部署（持续监控取"当前在跑的版本"用） */
    Deployment findFirstByProjectIdAndEnvironmentIdOrderByCreatedAtDesc(String projectId, String environmentId);
    boolean existsByArtifactId(String artifactId);
    boolean existsByEnvironmentId(String environmentId);
    boolean existsByEnvironmentIdAndStatusIn(String environmentId, List<TaskStatus> statuses);
    boolean existsByProjectIdAndStatusIn(String projectId, List<TaskStatus> statuses);
}
