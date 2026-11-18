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
import java.util.Set;
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

    /** 已请求取消的部署 id：runDeployment 据此区分 CANCELLED 与 FAILED（与 BuildService 同一语义） */
    private final Set<String> cancelled = ConcurrentHashMap.newKeySet();

    /** 同环境并发保护：同一台目标机同时只能有一个部署（软链切换/容器启停会互相踩踏）；待审批的也占位 */
    private void assertEnvIdle(String envId) {
        if (deploymentRepository.existsByEnvironmentIdAndStatusIn(envId,
                List.of(TaskStatus.PENDING, TaskStatus.PENDING_APPROVAL, TaskStatus.RUNNING))) {
            throw new ApiException("该环境有正在执行或排队（含待审批）的部署，请等待完成后再提交");
        }
    }

    /** 「检查空闲 + 建记录 + 提交执行」整体持同环境锁，消除竞态窗口；审批环境只建待审批记录 */
    private Deployment lockedCreateAndSubmit(Project project, Environment env, Artifact artifact,
                                             String rollbackOf, Integer rollbackRevision) {
        ReentrantLock lock = envLocks.computeIfAbsent(env.getId(), k -> new ReentrantLock());
        lock.lock();
        try {
            assertEnvIdle(env.getId());
            if (env.requiresApproval()) {
                return createRecord(project, env, artifact, rollbackOf, rollbackRevision, TaskStatus.PENDING_APPROVAL);
            }
            return createAndSubmit(project, env, artifact, rollbackOf, rollbackRevision);
        } finally {
            lock.unlock();
        }
    }

    /** 审批通过：从待审批进入执行队列；拒绝：落 REJECTED 终态 */
    public Deployment approve(String depId) {
        ReentrantLock lock = lockOf(depId);
        lock.lock();
        try {
            Deployment dep = deploymentRepository.findById(depId)
                    .orElseThrow(() -> ApiException.notFound("部署记录"));
            if (dep.getStatus() != TaskStatus.PENDING_APPROVAL) throw new ApiException("该部署不在待审批状态");
            String approver = currentOperator();
            dep.setStatus(TaskStatus.PENDING);
            Map<String, Object> result = dep.getResult() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(dep.getResult());
            result.put("approvedBy", approver);
            dep.setResult(result);
            dep = deploymentRepository.save(dep);
            Integer rev = dep.getRollbackRevision();
            try {
                deployExecutor.submit(() -> execute(depId, rev));
            } catch (java.util.concurrent.RejectedExecutionException e) {
                dep.setStatus(TaskStatus.FAILED);
                dep.setMessage("部署队列已满，请稍后重试");
                dep.setFinishedAt(LocalDateTime.now());
                deploymentRepository.save(dep);
                logStreamService.finish("deploy:" + depId);
                throw new ApiException("部署队列已满，请稍后重试");
            }
            return dep;
        } finally {
            lock.unlock();
        }
    }

    public Deployment reject(String depId, String reason) {
        Deployment dep = deploymentRepository.findById(depId)
                .orElseThrow(() -> ApiException.notFound("部署记录"));
        if (dep.getStatus() != TaskStatus.PENDING_APPROVAL) throw new ApiException("该部署不在待审批状态");
        dep.setStatus(TaskStatus.REJECTED);
        dep.setMessage("审批拒绝" + (reason == null || reason.isBlank() ? "" : "：" + reason));
        dep.setFinishedAt(LocalDateTime.now());
        dep = deploymentRepository.save(dep);
        logStreamService.finish("deploy:" + depId);
        notifyResult(dep);
        return dep;
    }

    private ReentrantLock lockOf(String depId) {
        Deployment dep = deploymentRepository.findById(depId).orElseThrow(() -> ApiException.notFound("部署记录"));
        return envLocks.computeIfAbsent(dep.getEnvironment().getId(), k -> new ReentrantLock());
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
        if (env.requiresApproval()) throw new ApiException("该环境开启了部署审批，批量部署暂不支持，请逐个发起");
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
                created.add(createRecord(project, env, artifact, null, null, TaskStatus.PENDING));
            }
            List<String> ids = created.stream().map(Deployment::getId).toList();
            try {
                deployExecutor.submit(() -> runBatch(ids));
            } catch (java.util.concurrent.RejectedExecutionException e) {
                // 队列满：整批标 FAILED，否则批量记录永远卡 PENDING
                for (Deployment d : created) {
                    d.setStatus(TaskStatus.FAILED);
                    d.setMessage("部署队列已满，请稍后重试");
                    d.setFinishedAt(LocalDateTime.now());
                    deploymentRepository.save(d);
                    logStreamService.finish("deploy:" + d.getId());
                }
                throw new ApiException("部署队列已满，请稍后重试");
            }
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
            TaskStatus finalStatus = runDeployment(id, null);
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
        // LessThanEqual + 排除自身：批量部署同秒落库的多条记录也能正确回退到前一条
        return deploymentRepository
                .findFirstByProjectIdAndEnvironmentIdAndStatusAndIdNotAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
                        target.getProject().getId(), target.getEnvironment().getId(), TaskStatus.SUCCESS,
                        target.getId(), target.getCreatedAt());
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
        Deployment dep = createRecord(project, env, artifact, rollbackOf, rollbackRevision, TaskStatus.PENDING);
        String depId = dep.getId();
        Integer revF = rollbackRevision;
        try {
            deployExecutor.submit(() -> execute(depId, revF));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // 队列满不处理的话记录会永远卡 PENDING（同环境锁也因此被占住，后续部署全部被拒）
            dep.setStatus(TaskStatus.FAILED);
            dep.setMessage("部署队列已满，请稍后重试");
            dep.setFinishedAt(LocalDateTime.now());
            deploymentRepository.save(dep);
            logStreamService.finish("deploy:" + depId);
            throw new ApiException("部署队列已满，请稍后重试");
        }
        return dep;
    }

    private Deployment createRecord(Project project, Environment env, Artifact artifact,
                                    String rollbackOf, Integer rollbackRevision, TaskStatus initialStatus) {
        Deployment dep = new Deployment();
        dep.setProject(project);
        dep.setEnvironment(env);
        dep.setArtifact(artifact);
        dep.setStatus(initialStatus); // 执行时置为 RUNNING；批量场景前序未完成时显示"排队中"；审批环境先待审批
        dep.setRollbackOf(rollbackOf);
        dep.setRollbackRevision(rollbackRevision);
        dep.setOperator(currentOperator());
        dep = deploymentRepository.save(dep);
        // logPath 依赖 @UuidGenerator 生成的 id，必须在 save 之后回填，否则落库 "deploy-null.log" 导致下载 404
        dep.setLogPath(props.logPath().resolve("deploy-" + dep.getId() + ".log").toString());
        return deploymentRepository.save(dep);
    }

    void execute(String depId, Integer rollbackRevision) {
        runDeployment(depId, rollbackRevision);
    }

    /** 执行单次部署（含健康检查与通知），返回数据库中的最终状态（供批量编排判断） */
    private TaskStatus runDeployment(String depId, Integer rollbackRevision) {
        // 重读库内记录：批量场景从创建到执行之间状态可能已被取消操作改写
        Deployment dep = deploymentRepository.findById(depId).orElse(null);
        if (dep == null) return TaskStatus.FAILED;
        if (dep.getStatus() == TaskStatus.CANCELLED) {
            cancelled.remove(depId);
            logStreamService.finish("deploy:" + depId);
            return TaskStatus.CANCELLED;
        }
        LogSink sink = logStreamService.sink("deploy:" + depId, props.logPath().resolve("deploy-" + depId + ".log"));
        try {
            dep.setStatus(TaskStatus.RUNNING);
            dep.setStartedAt(LocalDateTime.now());
            dep = deploymentRepository.save(dep);

            Deployment depRef = dep;
            Deployer deployer = deployers.stream().filter(d -> d.kind() == depRef.getEnvironment().getKind())
                    .findFirst().orElseThrow(() -> new ApiException("该环境类型暂无部署器"));
            Path artifactFile = artifactService.resolveFile(dep.getArtifact());
            // 上传/执行前校验制品完整性：sha256 与入库值不一致说明磁盘文件损坏，拒绝出库
            if (artifactService.verifyChecksum(dep.getArtifact())) {
                sink.line("[forge] 制品 sha256 校验通过");
            }

            DeployContext ctx = DeployContext.builder()
                    .project(dep.getProject())
                    .environment(dep.getEnvironment())
                    .artifact(dep.getArtifact())
                    .log(sink)
                    .settings(settingService)
                    .sshService(sshService)
                    .artifactFile(artifactFile)
                    .onCancel(h -> taskRegistry.register(depId, h))
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
            boolean wasCancelled = cancelled.remove(depId);
            sink.line("");
            sink.line(wasCancelled ? "[forge] 部署已取消" : "[forge] 部署失败: " + e.getMessage());
            if (!wasCancelled) log.warn("deploy {} failed", depId, e);
            dep.setStatus(wasCancelled ? TaskStatus.CANCELLED : TaskStatus.FAILED);
            dep.setMessage(wasCancelled ? "已取消" : e.getMessage());
        } finally {
            // 健康检查阶段取消不会被中断（部署照常收尾），标记必须清掉防止集合膨胀
            cancelled.remove(depId);
            dep.setFinishedAt(LocalDateTime.now());
            deploymentRepository.save(dep);
            taskRegistry.unregister(depId);
            logStreamService.finish("deploy:" + depId);
            notifyResult(dep);
        }
        return deploymentRepository.findById(depId).map(Deployment::getStatus).orElse(TaskStatus.FAILED);
    }

    private void notifyResult(Deployment dep) {
        String title = switch (String.valueOf(dep.getStatus())) {
            case "SUCCESS" -> "Forge 部署成功";
            case "CANCELLED" -> "Forge 部署已取消";
            default -> "Forge 部署失败";
        };
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
        // 待审批的部署可由发起人撤回（runDeployment 执行前会重读状态并跳过）
        if (dep.getStatus() == TaskStatus.PENDING || dep.getStatus() == TaskStatus.PENDING_APPROVAL) {
            // 排队/待审批中的部署：直接落终态；runDeployment 执行前会重读状态并跳过
            cancelled.add(depId);
            dep.setStatus(TaskStatus.CANCELLED);
            dep.setMessage("已取消（排队中）");
            dep.setFinishedAt(LocalDateTime.now());
            deploymentRepository.save(dep);
            logStreamService.finish("deploy:" + depId);
            return true;
        }
        if (dep.getStatus() != TaskStatus.RUNNING) return false;
        cancelled.add(depId);
        // 关闭已登记的句柄（SSH 会话断开 → 上传/exec 立即失败；本地进程整树击杀 → helm/compose 退出）
        return taskRegistry.cancel(depId);
    }

    private String currentOperator() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth == null || auth.getPrincipal() == null ? "system" : String.valueOf(auth.getPrincipal());
    }
}
