package com.forge.deploy;

import com.forge.config.ForgeProperties;
import com.forge.domain.Artifact;
import com.forge.domain.Deployment;
import com.forge.domain.Environment;
import com.forge.domain.EnvKind;
import com.forge.domain.Project;
import com.forge.domain.TaskStatus;
import com.forge.infra.log.LogStreamService;
import com.forge.infra.process.TaskRegistry;
import com.forge.infra.ssh.SshService;
import com.forge.project.EnvironmentService;
import com.forge.project.ProjectService;
import com.forge.setting.NotifyService;
import com.forge.setting.SettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** assertEnvIdle check-then-act 竞态（P1-3）与 deploy logPath 回填（P1-1） */
class DeployServiceTest {

    @TempDir
    Path dir;

    com.forge.domain.DeploymentRepository repo;
    com.forge.domain.ArtifactRepository artifactRepository;
    ProjectService projectService;
    EnvironmentService environmentService;
    com.forge.build.ArtifactService artifactService;
    HealthChecker healthChecker;
    LogStreamService logStreamService;
    TaskRegistry taskRegistry;
    SettingService settingService;
    NotifyService notifyService;
    SshService sshService;
    ForgeProperties props;
    ThreadPoolExecutor deployExecutor;

    DeployService service;

    /** 内存表 + save 延迟：放大 check-then-act 窗口，让旧代码的竞态确定性复现 */
    final Map<String, Deployment> table = new ConcurrentHashMap<>();
    final AtomicInteger idGen = new AtomicInteger();

    @BeforeEach
    void setup() {
        repo = mock(com.forge.domain.DeploymentRepository.class);
        artifactRepository = mock(com.forge.domain.ArtifactRepository.class);
        projectService = mock(ProjectService.class);
        environmentService = mock(EnvironmentService.class);
        artifactService = mock(com.forge.build.ArtifactService.class);
        healthChecker = mock(HealthChecker.class);
        logStreamService = new LogStreamService();
        taskRegistry = new TaskRegistry();
        settingService = mock(SettingService.class);
        notifyService = mock(NotifyService.class);
        sshService = mock(SshService.class);
        deployExecutor = mock(ThreadPoolExecutor.class);
        props = new ForgeProperties();
        props.setDataDir(dir.resolve("data").toString());
        props.setWorkDir(dir.resolve("work").toString());
        props.setArtifactDir(dir.resolve("artifacts").toString());
        props.setLogDir(dir.resolve("logs").toString());

        when(repo.save(any())).thenAnswer(inv -> {
            Deployment d = inv.getArgument(0);
            Thread.sleep(200); // 窗口放大
            if (d.getId() == null) d.setId("gen-" + idGen.incrementAndGet());
            table.put(d.getId(), d);
            return d;
        });
        when(repo.findById(anyString())).thenAnswer(inv ->
                Optional.ofNullable(table.get((String) inv.getArgument(0))));
        when(repo.existsByEnvironmentIdAndStatusIn(anyString(), any())).thenAnswer(inv -> {
            String envId = inv.getArgument(0);
            @SuppressWarnings("unchecked")
            List<TaskStatus> st = inv.getArgument(1);
            return table.values().stream()
                    .anyMatch(d -> envId.equals(d.getEnvironment().getId()) && st.contains(d.getStatus()));
        });

        Deployer sshDeployer = mock(Deployer.class);
        when(sshDeployer.kind()).thenReturn(EnvKind.SSH);

        service = new DeployService(repo, artifactRepository, projectService, environmentService,
                artifactService, List.of(sshDeployer), healthChecker, logStreamService, taskRegistry,
                settingService, notifyService, sshService, props, deployExecutor);
    }

    private Project project(String id) {
        Project p = new Project();
        p.setId(id);
        p.setCode("demo");
        p.setName("Demo");
        return p;
    }

    private Environment env() {
        Environment e = new Environment();
        e.setId("env-1");
        e.setName("prod");
        e.setKind(EnvKind.SSH);
        return e;
    }

    private Artifact artifact(Project p) {
        Artifact a = new Artifact();
        a.setId("art-1");
        a.setProject(p);
        a.setVersion("v1");
        return a;
    }

    private void stubStart(Project p, Environment e, Artifact a) {
        when(projectService.require(p.getId())).thenReturn(p);
        when(environmentService.require(e.getId())).thenReturn(e);
        when(artifactRepository.findById(a.getId())).thenReturn(Optional.of(a));
    }

    @Test
    void concurrentStartOnSameEnvAllowsExactlyOne() throws Exception {
        Project p = project("p1");
        Environment e = env();
        Artifact a = artifact(p);
        stubStart(p, e, a);

        CyclicBarrier barrier = new CyclicBarrier(2);
        List<Integer> outcomes = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        Runnable racer = () -> {
            try {
                barrier.await();
                service.start("p1", "env-1", "art-1");
                outcomes.add(0); // 成功
            } catch (Exception ex) {
                outcomes.add(ex.getMessage() != null && ex.getMessage().contains("该环境有正在执行") ? 1 : 2);
            }
        };
        Thread t1 = new Thread(racer), t2 = new Thread(racer);
        t1.start();
        t2.start();
        t1.join(10_000);
        t2.join(10_000);

        // 旧代码：两线程都在对方 save 生效前通过 assertEnvIdle → 两个 0（双成功竞态）
        assertEquals(1, outcomes.stream().filter(o -> o == 0).count(), "应恰一个成功: " + outcomes);
        assertEquals(1, outcomes.stream().filter(o -> o == 1).count(), "应恰一个被拒: " + outcomes);
        assertEquals(0, outcomes.stream().filter(o -> o == 2).count(), "不应有其他错误: " + outcomes);
    }

    @Test
    void startBackfillsLogPathWithGeneratedId() {
        Project p = project("p1");
        Environment e = env();
        Artifact a = artifact(p);
        stubStart(p, e, a);

        Deployment dep = service.start("p1", "env-1", "art-1");

        // 旧代码：save 前 getId() 为 null → "deploy-null.log"
        assertEquals(props.logPath().resolve("deploy-" + dep.getId() + ".log").toString(), dep.getLogPath());

        ArgumentCaptor<Deployment> cap = ArgumentCaptor.forClass(Deployment.class);
        verify(repo, atLeast(1)).save(cap.capture());
        assertTrue(cap.getAllValues().stream().noneMatch(d -> String.valueOf(d.getLogPath()).contains("null")),
                "任何一次 save 都不允许携带 null 日志路径");
    }
}
