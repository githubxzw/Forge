package com.forge.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArtifactRepository extends JpaRepository<Artifact, String> {
    Page<Artifact> findByProjectIdOrderByCreatedAtDesc(String projectId, Pageable pageable);
    Page<Artifact> findByOrderByCreatedAtDesc(Pageable pageable);
    List<Artifact> findByProjectIdAndTypeOrderByCreatedAtDesc(String projectId, ArtifactType type);
    List<Artifact> findByProjectId(String projectId);
}
