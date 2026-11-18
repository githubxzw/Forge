package com.forge.build;

import com.forge.domain.Project;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 内置 chart 的 values 渲染与健康检查探针联动（P0-5：enabled 不再硬编码 false） */
class HelmBuilderValuesTest {

    @Test
    void healthCheckDisabledWhenProjectUnconfigured() {
        String yaml = HelmBuilder.renderBuiltinValues(2, "reg/demo", "v1", 8080, false, "/", null, 0);
        assertFalse(yaml.contains("enabled: true"));
        assertTrue(yaml.contains("enabled: false"));
        assertTrue(yaml.contains("replicaCount: 2"));
        assertTrue(yaml.contains("repository: reg/demo"));
        assertTrue(yaml.contains("tag: \"v1\""));
        assertTrue(yaml.contains("containerPort: 8080"));
        assertFalse(yaml.contains("ingress:\n  enabled: true"), "未配域名不出 Ingress");
        assertFalse(yaml.contains("autoscaling:\n  enabled: true"), "未配 maxReplicas 不出 HPA");
    }

    @Test
    void healthCheckEnabledWithConfiguredPath() {
        String yaml = HelmBuilder.renderBuiltinValues(1, "demo", "v2", 80, true, "/actuator/health", null, 0);
        assertTrue(yaml.contains("enabled: true"));
        assertTrue(yaml.contains("path: /actuator/health"));
    }

    @Test
    void ingressAndHpaEnabledFromBuildConfig() {
        String yaml = HelmBuilder.renderBuiltinValues(2, "demo", "v3", 8080, true, "/health",
                "app.example.com", 5);
        assertTrue(yaml.contains("ingress:\n  enabled: true"));
        assertTrue(yaml.contains("host: \"app.example.com\""));
        assertTrue(yaml.contains("autoscaling:\n  enabled: true"));
        assertTrue(yaml.contains("minReplicas: 2"), "HPA 下限取副本数");
        assertTrue(yaml.contains("maxReplicas: 5"));
    }

    @Test
    void healthCheckPathFromProjectConfig() {
        Project p = new Project();
        assertNull(HelmBuilder.healthCheckPath(p));

        p.setHealthCheck(Map.of("path", "/actuator/health", "port", "8081"));
        assertEquals("/actuator/health", HelmBuilder.healthCheckPath(p));
    }

    @Test
    void healthCheckPathDerivedFromUrl() {
        Project p = new Project();
        p.setHealthCheck(Map.of("url", "http://10.0.0.1:8080/healthz"));
        assertEquals("/healthz", HelmBuilder.healthCheckPath(p));

        p.setHealthCheck(Map.of("url", "http://10.0.0.1:8080"));
        assertEquals("/", HelmBuilder.healthCheckPath(p));
    }
}
