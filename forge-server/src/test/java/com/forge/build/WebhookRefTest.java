package com.forge.build;

import com.forge.domain.Project;
import com.forge.domain.ProjectType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Webhook 触发判定（P1-4）：tag 始终触发、分支按 webhookBranches 过滤、默认仅默认分支 */
class WebhookRefTest {

    private Project project(Map<String, Object> buildConfig) {
        Project p = new Project();
        p.setType(ProjectType.BACKEND);
        p.setBuildConfig(buildConfig);
        return p;
    }

    @Test
    void tagPushAlwaysTriggers() {
        assertEquals("v1.2.3", WebhookController.resolveBuildRef(project(null), "main", "refs/tags/v1.2.3"));
        // 配了分支过滤也不影响 tag（打标签即发布意图）
        assertEquals("v2.0", WebhookController.resolveBuildRef(
                project(Map.of("webhookBranches", List.of("main"))), "main", "refs/tags/v2.0"));
    }

    @Test
    void defaultBranchTriggersWithoutConfig() {
        assertEquals("main", WebhookController.resolveBuildRef(project(null), "main", "refs/heads/main"));
        assertNull(WebhookController.resolveBuildRef(project(null), "main", "refs/heads/dev"));
    }

    @Test
    void branchFilterWhitelist() {
        Project p = project(Map.of("webhookBranches", List.of("main", "release")));
        assertEquals("release", WebhookController.resolveBuildRef(p, "main", "refs/heads/release"));
        assertNull(WebhookController.resolveBuildRef(p, "main", "refs/heads/feature/x"));
    }

    @Test
    void missingRefFallsBackToDefaultBranch() {
        assertEquals("master", WebhookController.resolveBuildRef(project(null), "master", null));
        assertEquals("master", WebhookController.resolveBuildRef(project(null), "master", ""));
    }

    @Test
    void defaultTargetsByProjectType() {
        assertEquals(List.of("FRONTEND_TAR"), WebhookController.defaultTargetsOf(projectWithType(ProjectType.FRONTEND)));
        assertEquals(List.of("BACKEND_JAR"), WebhookController.defaultTargetsOf(projectWithType(ProjectType.BACKEND)));
        assertEquals(List.of("FRONTEND_TAR", "BACKEND_JAR"), WebhookController.defaultTargetsOf(projectWithType(ProjectType.FULL)));
    }

    private Project projectWithType(ProjectType type) {
        Project p = new Project();
        p.setType(type);
        return p;
    }
}
