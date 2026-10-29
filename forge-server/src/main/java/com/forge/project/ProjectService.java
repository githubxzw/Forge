package com.forge.project;

import com.forge.common.ApiException;
import com.forge.config.ForgeProperties;
import com.forge.domain.Artifact;
import com.forge.domain.ArtifactType;
import com.forge.domain.Project;
import com.forge.domain.ProjectRepository;
import com.forge.domain.ProjectType;
import com.forge.build.TarUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectRepository repository;
    private final com.forge.domain.BuildTaskRepository buildTaskRepository;
    private final com.forge.domain.ArtifactRepository artifactRepository;
    private final com.forge.domain.DeploymentRepository deploymentRepository;
    private final ForgeProperties props;

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
        p.setWebhookSecret(newSecret());
        return repository.save(p);
    }

    @Transactional
    public Project update(String id, Project in) {
        Project p = require(id);
        if (in.getName() != null) p.setName(in.getName());
        if (in.getType() != null) p.setType(in.getType());
        if (in.getRepoUrl() != null) p.setRepoUrl(in.getRepoUrl());
        // 空串表示清除凭据（转公开仓库）
        if (in.getGitCredentialId() != null) {
            p.setGitCredentialId(in.getGitCredentialId().isBlank() ? null : in.getGitCredentialId());
        }
        if (in.getDefaultBranch() != null) p.setDefaultBranch(in.getDefaultBranch());
        if (in.getStack() != null) p.setStack(in.getStack());
        if (in.getBuildConfig() != null) p.setBuildConfig(in.getBuildConfig());
        if (in.getHealthCheck() != null) p.setHealthCheck(in.getHealthCheck());
        return repository.save(p);
    }

    /** 级联删除：部署记录 → 制品（含文件）→ 构建任务 → 项目 */
    @Transactional
    public void delete(String id) {
        Project p = require(id);
        // 运行中的构建/部署会把已删除的行在 finally 里重新 save 回来（孤儿 RUNNING 记录），先拒绝
        if (buildTaskRepository.existsByProjectIdAndStatusIn(id,
                List.of(com.forge.domain.TaskStatus.PENDING, com.forge.domain.TaskStatus.RUNNING))) {
            throw new ApiException("该项目有正在执行或排队的构建，无法删除");
        }
        if (deploymentRepository.existsByProjectIdAndStatusIn(id,
                List.of(com.forge.domain.TaskStatus.PENDING, com.forge.domain.TaskStatus.RUNNING))) {
            throw new ApiException("该项目有正在执行或排队的部署，无法删除");
        }
        deploymentRepository.deleteAll(deploymentRepository.findByProjectId(id));
        List<Artifact> artifacts = artifactRepository.findByProjectId(id);
        for (Artifact a : artifacts) {
            if (a.getType() != ArtifactType.IMAGE) {
                try {
                    java.nio.file.Path file = props.artifactPath().resolve(a.getRef());
                    java.nio.file.Files.deleteIfExists(file);
                } catch (Exception ignore) {}
            }
        }
        // 制品文件删完后清理该项目可能已空的制品目录
        TarUtils.deleteDir(props.artifactPath().resolve(p.getCode()));
        artifactRepository.deleteAll(artifacts);
        buildTaskRepository.deleteAll(buildTaskRepository.findByProjectId(id));
        repository.deleteById(id);
    }

    /** 重新生成 Webhook 密钥（旧的立即失效） */
    @Transactional
    public String regenerateWebhookSecret(String id) {
        Project p = require(id);
        p.setWebhookSecret(newSecret());
        repository.save(p);
        return p.getWebhookSecret();
    }

    private static String newSecret() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }
}
