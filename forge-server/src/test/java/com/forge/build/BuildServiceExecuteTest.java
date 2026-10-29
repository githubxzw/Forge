package com.forge.build;

import com.forge.common.ApiException;
import com.forge.config.ForgeProperties;
import com.forge.domain.Artifact;
import com.forge.domain.BuildTarget;
import com.forge.domain.BuildTask;
import com.forge.domain.Project;
import com.forge.domain.TaskStatus;
import com.forge.infra.git.GitClient;
import com.forge.infra.log.LogStreamService;
import com.forge.infra.process.TaskRegistry;
import com.forge.project.ProjectService;
import com.forge.setting.SettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 排队取消语义 / 工作目录 finally 清理 / logPath 回填（旧代码三处全红） */
class BuildServiceExecuteTest {

    @TempDir
    Path dir;

    com.forge.domain.BuildTaskRepository buildTaskRepository;
    ProjectService projectService;
    ArtifactService artifactService;
    GitClient gitClient;
    com.forge.credential.CredentialService credentialService;
    ThreadPoolExecutor buildExecutor;
    ForgeProperties props;
    LogStreamService logStreamService;
    Builder builder;

    BuildService service;

    @BeforeEach
    void setup() {
        buildTaskRepository = mock(com.forge.domain.BuildTaskRepository.class);
        projectService = mock(ProjectService.class);
        artifactService = mock(ArtifactService.class);
        gitClient = mock(GitClient.class);
        credentialService = mock(com.forge.credential.CredentialService.class);
        buildExecutor = mock(ThreadPoolExecutor.class); // submit 静默丢弃，测试内直接调 execute()
        props = new ForgeProperties();
        props.setDataDir(dir.resolve("data").toString());
        props.setWorkDir(dir.resolve("work").toString());
        props.setArtifactDir(dir.resolve("artifacts").toString());
        props.setLogDir(dir.resolve("logs").toString());
        logStreamService = new LogStreamService();
        builder = mock(Builder.class);
        when(builder.target()).thenReturn(BuildTarget.BACKEND_JAR);
        service = new BuildService(buildTaskRepository, projectService, artifactService, gitClient,
                logStreamService, new TaskRegistry(), props, mock(SettingService.class),
                credentialService, List.of(builder), buildExecutor);
    }

    private Project project() {
        Project p = new Project();
        p.setId("p1");
        p.setCode("demo");
        p.setName("Demo");
        p.setRepoUrl("https://example.com/demo.git");
        p.setDefaultBranch("main");
        return p;
    }

    private BuildTask task(Project p) {
        BuildTask t = new BuildTask();
        t.setId("t1");
        t.setProject(p);
        t.setBranch("main");
        t.setTargets(List.of(BuildTarget.BACKEND_JAR.name()));
        t.setStatus(TaskStatus.PENDING);
        return t;
    }

    @Test
    void executeSkipsTaskCancelledWhileQueued() {
        BuildTask t = task(project());
        t.setStatus(TaskStatus.CANCELLED);
        when(buildTaskRepository.findById("t1")).thenReturn(Optional.of(t));

        service.execute("t1", "p1", "main", List.of(BuildTarget.BACKEND_JAR));

        // 旧代码：立即置 RUNNING 并完整 clone —— 断言不 save、不查项目即红
        verify(buildTaskRepository, never()).save(any());
        verify(projectService, never()).require(anyString());
        assertFalse(Files.exists(props.workPath().resolve("demo").resolve("t1")));
    }

    @Test
    void executeCleansWorkDirOnFailure() {
        Project p = project();
        BuildTask t = task(p);
        when(buildTaskRepository.findById("t1")).thenReturn(Optional.of(t));
        when(buildTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(projectService.require("p1")).thenReturn(p);
        when(gitClient.ensureRepo(anyString(), anyString(), any(), any(), any(), any()))
                .thenThrow(new ApiException(500, "git 挂了"));

        service.execute("t1", "p1", "main", List.of(BuildTarget.BACKEND_JAR));

        Path workDir = props.workPath().resolve("demo").resolve("t1");
        assertFalse(Files.exists(workDir), "失败后工作目录必须清理（旧代码仅在成功分支清理）");

        ArgumentCaptor<BuildTask> cap = ArgumentCaptor.forClass(BuildTask.class);
        verify(buildTaskRepository, atLeast(1)).save(cap.capture());
        BuildTask last = cap.getValue();
        assertEquals(TaskStatus.FAILED, last.getStatus());
        assertTrue(last.getMessage().contains("git 挂了"), last.getMessage());
    }

    @Test
    void executeCleansWorkDirOnSuccess() {
        Project p = project();
        BuildTask t = task(p);
        when(buildTaskRepository.findById("t1")).thenReturn(Optional.of(t));
        when(buildTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(projectService.require("p1")).thenReturn(p);
        when(gitClient.ensureRepo(eq("https://example.com/demo.git"), eq("main"), any(), any(), any(), any()))
                .thenReturn("abc123def456");
        Artifact artifact = new Artifact();
        when(builder.build(any())).thenReturn(artifact);
        when(artifactService.attachAndSave(any(), any(), any())).thenReturn(artifact);

        service.execute("t1", "p1", "main", List.of(BuildTarget.BACKEND_JAR));

        Path workDir = props.workPath().resolve("demo").resolve("t1");
        assertFalse(Files.exists(workDir), "成功后工作目录必须清理");

        ArgumentCaptor<BuildTask> cap = ArgumentCaptor.forClass(BuildTask.class);
        verify(buildTaskRepository, atLeast(1)).save(cap.capture());
        assertEquals(TaskStatus.SUCCESS, cap.getValue().getStatus());
        assertEquals("abc123def456", cap.getValue().getCommit());
    }

    @Test
    void startBackfillsLogPathWithGeneratedId() {
        Project p = project();
        when(projectService.require("p1")).thenReturn(p);
        when(buildTaskRepository.save(any())).thenAnswer(inv -> {
            BuildTask t = inv.getArgument(0);
            if (t.getId() == null) t.setId("generated-id-1");
            return t;
        });

        BuildTask created = service.start("p1", "main", List.of("BACKEND_JAR"));

        // 旧代码：save 前 getId() 为 null → 落库 "build-null.log"，下载接口按库内路径 404
        assertEquals(props.logPath().resolve("build-generated-id-1.log").toString(), created.getLogPath());
        ArgumentCaptor<BuildTask> cap = ArgumentCaptor.forClass(BuildTask.class);
        verify(buildTaskRepository, atLeast(2)).save(cap.capture());
        assertTrue(cap.getAllValues().stream().noneMatch(t -> String.valueOf(t.getLogPath()).contains("null")),
                "任何一次 save 都不允许携带 null 日志路径");
    }
}
