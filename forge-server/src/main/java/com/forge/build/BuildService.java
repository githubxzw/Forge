package com.forge.build;

import com.forge.common.ApiException;
import com.forge.domain.Artifact;
import com.forge.domain.BuildTask;
import com.forge.domain.BuildTarget;
import com.forge.domain.Project;
import com.forge.domain.TaskStatus;
import com.forge.infra.log.LogSink;
import com.forge.infra.process.ProcessRunner;
import com.forge.infra.process.TaskRegistry;
import com.forge.setting.SettingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;

/** 构建编排：任务落库 → 线程池执行（检出代码 → 依序跑各 Builder → 制品入库） */
@Slf4j
@Service
public class BuildService {
    private static final List<BuildTarget> ORDER = List.of(
            BuildTarget.FRONTEND_TAR, BuildTarget.BACKEND_JAR, BuildTarget.IMAGE, BuildTarget.CHART);

    private final com.forge.domain.BuildTaskRepository buildTaskRepository;
    private final com.forge.project.ProjectService projectService;
    private final ArtifactService artifactService;
    private final com.forge.infra.git.GitClient gitClient;
    private final com.forge.infra.log.LogStreamService logStreamService;
    private final TaskRegistry taskRegistry;
    private final com.forge.config.ForgeProperties props;
    private final SettingService settingService;
    private final Map<BuildTarget, Builder> builders;
    private final ThreadPoolExecutor buildExecutor;

    private final Set<String> cancelled = ConcurrentHashMap.newKeySet();

    public BuildService(com.forge.domain.BuildTaskRepository buildTaskRepository,
                        com.forge.project.ProjectService projectService,
                        ArtifactService artifactService,
                        com.forge.infra.git.GitClient gitClient,
                        com.forge.infra.log.LogStreamService logStreamService,
                        TaskRegistry taskRegistry,
                        com.forge.config.ForgeProperties props,
                        SettingService settingService,
                        List<Builder> builderList,
                        @Qualifier("buildExecutor") ThreadPoolExecutor buildExecutor) {
        this.buildTaskRepository = buildTaskRepository;
        this.projectService = projectService;
        this.artifactService = artifactService;
        this.gitClient = gitClient;
        this.logStreamService = logStreamService;
        this.taskRegistry = taskRegistry;
        this.props = props;
        this.settingService = settingService;
        this.builders = new EnumMap<>(BuildTarget.class);
        builderList.forEach(b -> builders.put(b.target(), b));
        this.buildExecutor = buildExecutor;
    }

    public BuildTask start(String projectId, String branch, List<String> targets) {
        Project project = projectService.require(projectId);
        if (branch == null || branch.isBlank()) branch = project.getDefaultBranch() == null ? "main" : project.getDefaultBranch();

        List<BuildTarget> parsed = new ArrayList<>();
        for (String t : targets) {
            try {
                BuildTarget bt = BuildTarget.valueOf(t);
                if (!parsed.contains(bt)) parsed.add(bt);
            } catch (IllegalArgumentException e) {
                throw new ApiException("未知构建目标: " + t);
            }
        }
        if (parsed.isEmpty()) throw new ApiException("请至少选择一个构建目标");
        for (BuildTarget t : parsed) {
            if (!builders.containsKey(t)) throw new ApiException("构建器缺失: " + t);
        }
        parsed.sort(java.util.Comparator.comparingInt(ORDER::indexOf));

        BuildTask task = new BuildTask();
        task.setProject(project);
        task.setBranch(branch);
        task.setTargets(parsed.stream().map(Enum::name).toList());
        task.setStatus(TaskStatus.PENDING);
        task.setLogPath(props.logPath().resolve("build-" + task.getId() + ".log").toString());
        task = buildTaskRepository.save(task);

        String taskId = task.getId();
        String branchF = branch;
        List<BuildTarget> targetsF = parsed;
        buildExecutor.submit(() -> execute(taskId, projectId, branchF, targetsF));
        return task;
    }

    void execute(String taskId, String projectId, String branch, List<BuildTarget> targets) {
        LogSink sink = logStreamService.sink("build:" + taskId, props.logPath().resolve("build-" + taskId + ".log"));
        BuildTask task = null;
        try {
            task = buildTaskRepository.findById(taskId).orElseThrow();
            Project project = projectService.require(projectId);
            task.setStatus(TaskStatus.RUNNING);
            task.setStartedAt(LocalDateTime.now());
            task = buildTaskRepository.save(task);

            String code = project.getCode();
            Path workDir = props.workPath().resolve(code).resolve(taskId);
            Path src = workDir.resolve("src");
            Files.createDirectories(workDir);

            sink.line("[forge] 检出代码: " + project.getRepoUrl() + " @ " + branch);
            String commit = gitClient.ensureRepo(project.getRepoUrl(), branch, src, sink,
                    p -> taskRegistry.register(taskId, (java.io.Closeable) () -> ProcessRunner.killTree(p)));
            requireNotCancelled(taskId);
            sink.line("[forge] commit: " + commit);

            String version = genVersion(branch, commit);
            task.setCommit(commit);
            task.setVersion(version);
            task = buildTaskRepository.save(task);

            BuildContext ctx = new BuildContext(project, task, workDir, branch, commit, version, sink,
                    settingService, props.artifactPath());
            for (BuildTarget target : targets) {
                requireNotCancelled(taskId);
                sink.line("");
                sink.line("[forge] ===== 构建目标: " + target + " =====");
                Artifact artifact = builders.get(target).build(ctx);
                artifactService.attachAndSave(artifact, project, task);
                sink.line("[forge] 制品已入库: " + artifact.getType() + " " + artifact.getVersion());
            }

            task.setStatus(TaskStatus.SUCCESS);
            sink.line("");
            sink.line("[forge] 构建成功，版本: " + version);
            TarUtils.deleteDir(workDir); // 成功后清理工作目录
        } catch (Exception e) {
            boolean wasCancelled = cancelled.remove(taskId);
            sink.line("");
            sink.line(wasCancelled ? "[forge] 构建已取消" : "[forge] 构建失败: " + e.getMessage());
            if (!wasCancelled) log.warn("build {} failed", taskId, e);
            if (task != null) {
                task.setStatus(wasCancelled ? TaskStatus.CANCELLED : TaskStatus.FAILED);
                task.setMessage(wasCancelled ? "已取消" : e.getMessage());
            }
        } finally {
            if (task != null) {
                task.setFinishedAt(LocalDateTime.now());
                buildTaskRepository.save(task);
            }
            taskRegistry.unregister(taskId);
            logStreamService.finish("build:" + taskId);
        }
    }

    private void requireNotCancelled(String taskId) {
        if (cancelled.contains(taskId)) throw new ApiException(499, "任务已取消");
    }

    public boolean cancel(String taskId) {
        BuildTask task = buildTaskRepository.findById(taskId).orElseThrow(() -> ApiException.notFound("构建任务"));
        if (task.getStatus() == TaskStatus.PENDING) {
            cancelled.add(taskId);
            task.setStatus(TaskStatus.CANCELLED);
            task.setMessage("已取消（排队中）");
            task.setFinishedAt(LocalDateTime.now());
            buildTaskRepository.save(task);
            logStreamService.finish("build:" + taskId);
            return true;
        }
        if (task.getStatus() != TaskStatus.RUNNING) return false;
        cancelled.add(taskId);
        return taskRegistry.cancel(taskId);
    }

    public static String genVersion(String branch, String commit) {
        String b = branch.toLowerCase().replaceAll("[^a-z0-9._-]", "-").replaceAll("-+", "-");
        if (b.length() > 20) b = b.substring(0, 20);
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        String c = commit == null || commit.length() < 7 ? "local" : commit.substring(0, 7);
        return b + "-" + ts + "-" + c;
    }
}
