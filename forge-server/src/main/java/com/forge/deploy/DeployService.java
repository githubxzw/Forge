package com.forge.deploy;

import com.forge.build.ArtifactService;
import com.forge.common.ApiException;
import com.forge.domain.Artifact;
import com.forge.domain.Deployment;
import com.forge.domain.Environment;
import com.forge.domain.EnvKind;
import com.forge.domain.Project;
import com.forge.domain.TaskStatus;
import com.forge.infra.log.LogSink;
import com.forge.infra.log.LogStreamService;
import com.forge.infra.process.TaskRegistry;
import com.forge.project.ProjectService;
import com.forge.setting.NotifyService;
import com.forge.setting.SettingService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.locks.ReentrantLock;

/** 部署编排：落库 → 线程池执行（Deployer.deploy → 健康检查 → 更新状态 → 通知）；回滚生成新部署记录；支持批量顺序部署 */
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
    private final NotifyService notifyService;
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
                         NotifyService notifyService,
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
        this.notifyService = notifyService;
        this.sshService = sshService;
        this.props = props;
        this.deployExecutor = deployExecutor;
    }

    @Data
    public static class BatchItem {
        private String projectId;
        private String artifactId;
    }

    /** 同环境串行锁：assertEnvIdle 是 check-then-act，并发提交会同时通过检查互相踩踏 */
    private final ConcurrentHashMap<String, ReentrantLock> envLocks = new ConcurrentHashMap<>();

    /** 同环境并发保护：同一台目标机同时只能有一个部署（软链切换/容器启停会互相踩踏） */
    private void assertEnvIdle(String envId) {
        if (deploymentRepository.existsByEnvironmentIdAndStatusIn(envId, List.of(TaskStatus.PENDING, TaskStatus.RUNNING))) {
            throw new ApiException("该环境有正在执行或排队的部署，请等待完成后再提交");
        }
    }

    /** 「检查空闲 + 建记录 + 提交执行」整体持同环境锁，消除竞态窗口 */
    private Deployment lockedCreateAndSubmit(Project project, Environment env, Artifact artifact,
                                             String rollbackOf, Integer rollbackRevision) {
        ReentrantLock lock = envLocks.computeIfAbsent(env.getId(), k -> new ReentrantLock());
        lock.lock();
        try {
            assertEnvIdle(env.getId());
            return createAndSubmit(project, env, artifact, rollbackOf, rollbackRevision);
        } finally {
            lock.unlock();
        }
    }

    public Deployment start(String projectId, String envId, String artifactId) {
        Project project = projectService.require(projectId);
        Environment env = environmentService.require(envId);
        Artifact artifact = artifactRepository.findById(artifactId)
                .orElseThrow(() -> ApiException.notFound("制品"));
        if (!artifact.getProject().getId().equals(projectId)) {
            throw new ApiException("制品不属于该项目");
        }
        return lockedCreateAndSubmit(project, env, artifact, null, null);
    }

    /** 批量顺序部署：同一环境逐个部署，前序失败即中止，剩余标记"已取消" */
    public List<Deployment> startBatch(String envId, List<BatchItem> items) {
        if (items == null || items.isEmpty()) throw new ApiException("批量部署列表为空");
        Environment env = environmentService.require(envId);
        deployers.stream().filter(d -> d.kind() == env.getKind()).findFirst()
                .orElseThrow(() -> new ApiException("该环境类型暂无部署器"));

        ReentrantLock lock = envLocks.computeIfAbsent(envId, k -> new ReentrantLock());
        lock.lock();
        try {
            assertEnvIdle(envId);
            List<Deployment> created = new ArrayList<>();
            for (BatchItem item : items) {
                Project project = projectService.require(item.getProjectId());
                Artifact artifact = artifactRepository.findById(item.getArtifactId())
                        .orElseThrow(() -> ApiException.notFound("制品"));
                if (!artifact.getProject().getId().equals(item.getProjectId())) {
                    throw new ApiException("制品不属于项目: " + project.getCode());
                }
                created.add(createRecord(project, env, artifact, null, null));
            }
            List<String> ids = created.stream().map(Deployment::getId).toList();
            deployExecutor.submit(() -> runBatch(ids));
            return created;
        } finally {
            lock.unlock();
        }
    }

    private void runBatch(List<String> ids) {
        boolean stopped = false;
        for (String id : ids) {
            Deployment dep = deploymentRepository.findById(id).orElse(null);
            if (dep == null) continue;
            if (stopped) {
                dep.setStatus(TaskStatus.CANCELLED);
                dep.setMessage("前序部署失败，批量中止");
                dep.setFinishedAt(LocalDateTime.now());
                deploymentRepository.save(dep);
                logStreamService.finish("deploy:" + id);
                continue;
            }
            TaskStatus finalStatus = runDeployment(dep, null);
            if (finalStatus != TaskStatus.SUCCESS) stopped = true;
        }
    }

    /** 回滚预览：找到将回退到的历史版本（供确认框展示），无可回滚版本时返回 {none:true} */
    public Map<String, Object> rollbackPreview(String deploymentId) {
        Deployment target = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> ApiException.notFound("部署记录"));
        Deployment previous = findPreviousSuccess(target);
        Map<String, Object> result = new LinkedHashMap<>();
        if (previous == null) {
            result.put("none", true);
            return result;
        }
        result.put("none", false);
        result.put("version", previous.getArtifact().getVersion());
        result.put("deployedAt", String.valueOf(previous.getCreatedAt()));
        result.put("operator", previous.getOperator());
        return result;
    }

    private Deployment findPreviousSuccess(Deployment target) {
        return deploymentRepository
                .findFirstByProjectIdAndEnvironmentIdAndStatusAndCreatedAtBeforeOrderByCreatedAtDesc(
                        target.getProject().getId(), target.getEnvironment().getId(), TaskStatus.SUCCESS, target.getCreatedAt());
    }

    /** 回滚：撤销某次部署，回到它之前同项目同环境的最近一次成功版本。 */
    public Deployment rollback(String deploymentId) {
        Deployment target = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> ApiException.notFound("部署记录"));
        if (target.getStatus() != TaskStatus.SUCCESS) throw new ApiException("仅成功的部署可回滚");
        Deployment previous = findPreviousSuccess(target);
        if (previous == null) throw new ApiException("没有更早的成功部署可回滚");
        Integer revision = null;
        if (target.getEnvironment().getKind() == EnvKind.K8S && previous.getResult() != null
                && previous.getResult().get("revision") instanceof Number n) {
            revision = n.intValue();
        }
        return lockedCreateAndSubmit(target.getProject(), target.getEnvironment(), previous.getArtifact(),
                target.getId(), revision);
    }

    private Deployment createAndSubmit(Project project, Environment env, Artifact artifact,
                                       String rollbackOf, Integer rollbackRevision) {
        Deployment dep = createRecord(project, env, artifact, rollbackOf, rollbackRevision);
        String depId = dep.getId();
        Integer revF = rollbackRevision;
        deployExecutor.submit(() -> execute(depId, revF));
        return dep;
    }

    private Deployment createRecord(Project project, Environment env, Artifact artifact,
                                    String rollbackOf, Integer rollbackRevision) {
        Deployment dep = new Deployment();
        dep.setProject(project);
        dep.setEnvironment(env);
        dep.setArtifact(artifact);
        dep.setStatus(TaskStatus.PENDING); // 执行时置为 RUNNING；批量场景前序未完成时显示"排队中"
        dep.setRollbackOf(rollbackOf);
        dep.setOperator(currentOperator());
        dep = deploymentRepository.save(dep);
        // logPath 依赖 @UuidGenerator 生成的 id，必须在 save 之后回填，否则落库 "deploy-null.log" 导致下载 404
        dep.setLogPath(props.logPath().resolve("deploy-" + dep.getId() + ".log").toString());
        return deploymentRepository.save(dep);
    }

    void execute(String depId, Integer rollbackRevision) {
        Deployment dep = deploymentRepository.findById(depId).orElse(null);
        if (dep == null) return;
        runDeployment(dep, rollbackRevision);
    }

    /** 执行单次部署（含健康检查与通知），返回数据库中的最终状态（供批量编排判断） */
    private TaskStatus runDeployment(Deployment dep, Integer rollbackRevision) {
        String depId = dep.getId();
        LogSink sink = logStreamService.sink("deploy:" + depId, props.logPath().resolve("deploy-" + depId + ".log"));
        try {
            dep.setStatus(TaskStatus.RUNNING);
            dep.setStartedAt(LocalDateTime.now());
            dep = deploymentRepository.save(dep);

            Deployment depRef = dep;
            Deployer deployer = deployers.stream().filter(d -> d.kind() == depRef.getEnvironment().getKind())
                    .findFirst().orElseThrow(() -> new ApiException("该环境类型暂无部署器"));
            Path artifactFile = artifactService.resolveFile(dep.getArtifact());

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
            dep.setStatus(TaskStatus.FAILED);
            dep.setMessage(e.getMessage());
        } finally {
            dep.setFinishedAt(LocalDateTime.now());
            deploymentRepository.save(dep);
            taskRegistry.unregister(depId);
            logStreamService.finish("deploy:" + depId);
            notifyResult(dep);
        }
        return deploymentRepository.findById(depId).map(Deployment::getStatus).orElse(TaskStatus.FAILED);
    }

    private void notifyResult(Deployment dep) {
        String title = "Forge 部署" + ("SUCCESS".equals(String.valueOf(dep.getStatus())) ? "成功" : "失败");
        String msg = String.format("项目：%s\n环境：%s（%s）\n版本：%s\n操作人：%s%s",
                dep.getProject().getName(),
                dep.getEnvironment().getName(),
                dep.getEnvironment().getKind(),
                dep.getArtifact() == null ? "-" : dep.getArtifact().getVersion(),
                dep.getOperator() == null ? "-" : dep.getOperator(),
                dep.getMessage() == null ? "" : "\n原因：" + dep.getMessage());
        notifyService.send(title, msg);
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
