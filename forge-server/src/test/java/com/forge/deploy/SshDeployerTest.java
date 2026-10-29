package com.forge.deploy;

import com.forge.domain.Artifact;
import com.forge.domain.ArtifactType;
import com.forge.domain.Environment;
import com.forge.domain.EnvKind;
import com.forge.domain.Project;
import com.forge.infra.log.LogSink;
import com.forge.infra.ssh.SshExecutor;
import com.forge.infra.ssh.SshService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 清理旧 release 时必须保护 current 软链指向的目录（P1-4） */
class SshDeployerTest {

    @Test
    void cleanupCommandGuardsCurrentAndOffsetsTail() {
        String cmd = SshDeployer.cleanupCommand("/data/app/releases", "/data/app/current", 5);
        assertTrue(cmd.contains("tail -n +6"), "keep=5 应从第 6 个开始清理: " + cmd);
        assertTrue(cmd.contains("readlink"), "必须读出 current 指向: " + cmd);
        assertTrue(cmd.contains("grep -v -F"), "必须把 current 指向的目录从清理列表排除: " + cmd);
        assertTrue(cmd.contains("basename"), "排除的是目录名而非绝对路径: " + cmd);
        assertTrue(cmd.contains("xargs -r rm -rf"), "空列表时不能执行 rm: " + cmd);
    }

    @Test
    void deployExecutesGuardedCleanup() throws Exception {
        SshService sshService = mock(SshService.class);
        SshExecutor ssh = mock(SshExecutor.class);
        when(sshService.connect(any())).thenReturn(ssh);

        Environment env = new Environment();
        env.setId("e1");
        env.setName("prod");
        env.setKind(EnvKind.SSH);
        env.setConnConfig(Map.of(
                "host", "10.0.0.1",
                "deployDir", "/data/app",
                "keepReleases", 5));

        Project project = new Project();
        project.setId("p1");
        project.setCode("demo");

        Artifact artifact = new Artifact();
        artifact.setId("a1");
        artifact.setProject(project);
        artifact.setType(ArtifactType.JAR);
        artifact.setVersion("v9");

        DeployContext ctx = DeployContext.builder()
                .project(project)
                .environment(env)
                .artifact(artifact)
                .log(LogSink.NOOP)
                .sshService(sshService)
                .artifactFile(Path.of("/tmp/app.jar"))
                .build();

        new SshDeployer().deploy(ctx);

        ArgumentCaptor<String> cmds = ArgumentCaptor.forClass(String.class);
        verify(ssh, org.mockito.Mockito.atLeastOnce()).execOrThrow(cmds.capture(), any(), anyLong());
        List<String> all = cmds.getAllValues();
        // 旧代码：裸 "ls -1t | tail -n +6 | xargs -r rm -rf"，无 readlink 保护
        assertTrue(all.stream().anyMatch(c -> c.contains("readlink") && c.contains("/data/app/current")),
                "deploy 必须执行带 current 保护的清理命令: " + all);
    }
}
