package com.forge.deploy;

import com.forge.common.ApiException;
import com.forge.build.ArtifactService;
import com.forge.domain.Artifact;
import com.forge.domain.BuildTask;
import com.forge.domain.Deployment;
import com.forge.domain.Environment;
import com.forge.domain.EnvKind;
import com.forge.domain.Project;
import com.forge.domain.TaskStatus;
import com.forge.infra.log.LogSink;
import com.forge.infra.log.LogStreamService;
import com.forge.infra.process.TaskRegistry;
import com.forge.project.ProjectService;
import com.forge.setting.SettingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/** 部署编排：落库 → 线程池执行（Deployer.deploy → 健康检查 → 更新状态）；回滚生成新部署记录 */
@Slf4j
@Service
public class DeployService {
    private final com.forge.domain.DeploymentRepository deploymentRepository;
    private final com.forge.domain.ArtifactRepository artifactRepository;
    private final ProjectService projectService;
    private final com.forge.project.EnvironmentService environmentService;
    private final ArtifactService artifactService;
    private final List<Deployer> deployers;
    private final HealthChecker healthChecker;
    private final LogStreamService logStreamService;
    private final TaskRegistry taskRegistry;
    private final SettingService settingService;
    private final com.forge.infra.ssh.SshService sshService;
    private final com.forge.config.ForgeProperties props;
    private final ThreadPoolExecutor deployExecutor;

    public DeployService(com.forge.domain.DeploymentRepository deploymentRepository,
                         com.forge.domain.ArtifactRepository artifactRepository,
                         ProjectService projectService,
                         com.forge.project.EnvironmentService environmentService,
                         ArtifactService artifactService,
                         List<Deployer> deployers,
                         HealthChecker healthChecker,
                         LogStreamService logStreamService,
                         TaskRegistry taskRegistry,
                         SettingService settingService,
                         com.forge.infra.ssh.SshService sshService,
                         com.forge.config.ForgeProperties props,
                         @Qualifier("deployExecutor") ThreadPoolExecutor deployExecutor) {
        this.deploymentRepository = deploymentRepository;
        this.artifactRepository = artifactRepository;
        this.projectService = projectService;
        this.environmentService = environmentService;
        this.artifactService = artifactService;
        this.deployers = deployers;
        this.healthChecker = healthChecker;
        this.logStreamService = logStreamService;
        this.taskRegistry = taskRegistry;
        this.settingService = settingService;
        this.sshService = sshService;
        this.props = props;
        this.deployExecutor = deployExecutor;
    }

    public Deployment start(String projectId, String envId, String artifactId) {
        Project project = projectService.require(projectId);
        Environment env = environmentService.require(envId);
        Artifact artifact = artifactRepository.findById(artifactId)
                .orElseThrow(() -> ApiException.notFound("制品"));
        if (!artifact.getProject().getId().equals(projectId)) {
            throw new ApiException("制品不属于该项目");
        }
        if (env.getKind() == EnvKind.SSH && artifact.getType() == com.forge.domain.ArtifactType.TAR
                && project.getType() == com.forge.domain.ProjectType.FRONTEND) {
            // 合法：前端静态包部署
        }
        return submit(project, env, artifact, null, null);
    }

    /**
     * 回滚：撤销某次部署，回到它之前同项目同环境的最近一次成功版本。
     * 生成一条 rollbackOf 指向被撤销部署的新部署记录；K8S 优先 helm rollback 到历史 revision。
     */
    public Deployment rollback(String deploymentId) {
        Deployment target = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> ApiException.notFound("部署记录"));
        if (target.getStatus() != TaskStatus.SUCCESS) throw new ApiException("仅成功的部署可回滚");
        Deployment previous = deploymentRepository
                .findFirstByProjectIdAndEnvironmentIdAndStatusAndCreatedAtBeforeOrderByCreatedAtDesc(
                        target.getProject().getId(), target.getEnvironment().getId(), TaskStatus.SUCCESS, target.getCreatedAt());
        if (previous == null) throw new ApiException("没有更早的成功部署可回滚");
        Integer revision = null;
        if (target.getEnvironment().getKind() == EnvKind.K8S && previous.getResult() != null
                && previous.getResult().get("revision") instanceof Number n) {
            revision = n.intValue();
        }
        return submit(target.getProject(), target.getEnvironment(), previous.getArtifact(), target.getId(), revision);
    }

    private Deployment submit(Project project, Environment env, Artifact artifact,
                              String rollbackOf, Integer rollbackRevision) {
        Deployment dep = new Deployment();
        dep.setProject(project);
        dep.setEnvironment(env);
        dep.setArtifact(artifact);
        dep.setStatus(TaskStatus.RUNNING);
        dep.setRollbackOf(rollbackOf);
        dep.setOperator(currentOperator());
        dep.setLogPath(props.logPath().resolve("deploy-" + dep.getId() + ".log").toString());
        dep = deploymentRepository.save(dep);

        String depId = dep.getId();
        Integer revF = rollbackRevision;
        deployExecutor.submit(() -> execute(depId, revF));
        return dep;
    }

    void execute(String depId, Integer rollbackRevision) {
        LogSink sink = logStreamService.sink("deploy:" + depId, props.logPath().resolve("deploy-" + depId + ".log"));
        Deployment dep = null;
        try {
            dep = deploymentRepository.findById(depId).orElseThrow();
            dep.setStartedAt(LocalDateTime.now());
            dep = deploymentRepository.save(dep);

            Path artifactFile = artifactService.resolveFile(dep.getArtifact());
            Deployment depRef = dep;
            Deployer deployer = deployers.stream().filter(d -> d.kind() == depRef.getEnvironment().getKind())
                    .findFirst().orElseThrow(() -> new ApiException("该环境类型暂无部署器"));

            DeployContext ctx = DeployContext.builder()
                    .project(dep.getProject())
                    .environment(dep.getEnvironment())
                    .artifact(dep.getArtifact())
                    .log(sink)
                    .settings(settingService)
                    .sshService(sshService)
                    .artifactFile(artifactFile)
                    .rollbackRevision(rollbackRevision)
                    .build();

            Map<String, Object> result = new LinkedHashMap<>();
            result.putAll(deployer.deploy(ctx));

            sink.line("");
            Map<String, Object> health = healthChecker.check(dep.getProject(), dep.getEnvironment(), sink);
            result.putAll(health);
            dep.setResult(result);
            dep.setStatus(TaskStatus.SUCCESS);
            sink.line("");
            sink.line("[forge] 部署成功 ✔");
        } catch (Exception e) {
            sink.line("");
            sink.line("[forge] 部署失败: " + e.getMessage());
            log.warn("deploy {} failed", depId, e);
            if (dep != null) {
                dep.setStatus(TaskStatus.FAILED);
                dep.setMessage(e.getMessage());
            }
        } finally {
            if (dep != null) {
                dep.setFinishedAt(LocalDateTime.now());
                deploymentRepository.save(dep);
            }
            taskRegistry.unregister(depId);
            logStreamService.finish("deploy:" + depId);
        }
    }

    public boolean cancel(String depId) {
        Deployment dep = deploymentRepository.findById(depId).orElseThrow(() -> ApiException.notFound("部署记录"));
        if (dep.getStatus() != TaskStatus.RUNNING) return false;
        return taskRegistry.cancel(depId);
    }

    private String currentOperator() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth == null || auth.getPrincipal() == null ? "system" : String.valueOf(auth.getPrincipal());
    }
}
