package com.forge.deploy;

import com.forge.domain.Artifact;
import com.forge.domain.ArtifactType;
import com.forge.domain.Environment;
import com.forge.domain.Project;
import com.forge.infra.log.LogSink;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerDeployerTest {

    private DeployContext ctx(Project project, Environment env, Artifact artifact) {
        return DeployContext.builder()
                .project(project)
                .environment(env)
                .artifact(artifact)
                .log(LogSink.NOOP)
                .settings(null)
                .sshService(null)
                .artifactFile(null)
                .build();
    }

    private Project project(String code, Map<String, Object> buildConfig) {
        Project p = new Project();
        p.setCode(code);
        p.setBuildConfig(buildConfig);
        return p;
    }

    @Test
    void defaultPortMapping() {
        DockerDeployer d = new DockerDeployer();
        String yaml = d.renderCompose(ctx(
                project("demo-app", null),
                new Environment(),
                artifact("demo-app:1.0")));
        assertTrue(yaml.contains("image: demo-app:1.0"));
        assertTrue(yaml.contains("\"8080:8080\"")); // 默认端口映射
    }

    @Test
    void customPortsAndEnv() {
        Environment env = new Environment();
        env.setVars(Map.of("SPRING_PROFILES_ACTIVE", "prod", "helm.replicaCount", "2", "java.opts", "-Xmx1g"));
        String yaml = new DockerDeployer().renderCompose(ctx(
                project("demo", Map.of("ports", java.util.List.of("80:80", "443:443"))),
                env,
                artifact("reg.example.com/demo:2.0")));
        assertTrue(yaml.contains("\"80:80\""));
        assertTrue(yaml.contains("\"443:443\""));
        assertTrue(yaml.contains("SPRING_PROFILES_ACTIVE: \"prod\""));
        assertFalse(yaml.contains("helm.replicaCount")); // helm.* 不进容器环境
        assertFalse(yaml.contains("java.opts"));
    }

    private Artifact artifact(String ref) {
        Artifact a = new Artifact();
        a.setType(ArtifactType.IMAGE);
        a.setRef(ref);
        return a;
    }
}
