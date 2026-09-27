package com.forge.project;

import com.forge.common.ApiException;
import com.forge.domain.Project;
import com.forge.domain.ProjectRepository;
import com.forge.domain.ProjectType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectRepository repository;

    public List<Project> list() {
        return repository.findAll();
    }

    public Project require(String id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("项目"));
    }

    public Project create(Project p) {
        if (p.getCode() == null || !p.getCode().matches("[a-zA-Z0-9][a-zA-Z0-9._-]{1,62}")) {
            throw new ApiException("项目编码必须是 2-63 位字母数字（.-_）");
        }
        if (repository.existsByCode(p.getCode())) throw new ApiException("项目编码已存在: " + p.getCode());
        if (p.getDefaultBranch() == null || p.getDefaultBranch().isBlank()) p.setDefaultBranch("main");
        return repository.save(p);
    }

    @Transactional
    public Project update(String id, Project in) {
        Project p = require(id);
        if (in.getName() != null) p.setName(in.getName());
        if (in.getType() != null) p.setType(in.getType());
        if (in.getRepoUrl() != null) p.setRepoUrl(in.getRepoUrl());
        if (in.getDefaultBranch() != null) p.setDefaultBranch(in.getDefaultBranch());
        if (in.getStack() != null) p.setStack(in.getStack());
        if (in.getBuildConfig() != null) p.setBuildConfig(in.getBuildConfig());
        if (in.getHealthCheck() != null) p.setHealthCheck(in.getHealthCheck());
        return repository.save(p);
    }

    @Transactional
    public void delete(String id) {
        repository.deleteById(id);
    }
}
