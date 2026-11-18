package com.forge.build;

import com.forge.common.ApiException;
import com.forge.common.ApiResult;
import com.forge.domain.BuildTask;
import com.forge.domain.BuildTarget;
import com.forge.domain.BuildTrigger;
import com.forge.domain.Project;
import com.forge.domain.ProjectRepository;
import com.forge.domain.TaskStatus;
import com.forge.domain.BuildTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Git Webhook 自动构建（公开端点，凭 secret 校验）。
 * 兼容 GitHub（X-Hub-Signature-256 HMAC 签名）/ GitLab（X-Gitlab-Token）/ Gitee 及通用（?token=）。
 */
@Slf4j
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class WebhookController {
    private final ProjectRepository projectRepository;
    private final BuildTaskRepository buildTaskRepository;
    private final BuildService buildService;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper = com.forge.common.JsonUtils.mapper();

    @PostMapping("/{code}")
    public ApiResult<Map<String, Object>> trigger(
            @PathVariable String code,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-Gitlab-Token", required = false) String gitlabToken,
            @RequestParam(value = "token", required = false) String tokenParam,
            @RequestBody(required = false) String body) {

        Project project = projectRepository.findByCode(code)
                .orElseThrow(() -> ApiException.notFound("项目"));
        String secret = project.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new ApiException(403, "该项目未启用 Webhook");
        }
        boolean authed;
        if (signature != null && !signature.isBlank()) {
            authed = hmacVerify(secret, body == null ? "" : body, signature);
        } else if (gitlabToken != null && !gitlabToken.isBlank()) {
            authed = constantEq(secret, gitlabToken);
        } else if (tokenParam != null && !tokenParam.isBlank()) {
            authed = constantEq(secret, tokenParam);
        } else {
            authed = false;
        }
        if (!authed) {
            log.warn("Webhook 校验失败: project={}", code);
            throw new ApiException(401, "Webhook 校验失败");
        }

        // 解析 push ref：refs/heads/xxx 分支 / refs/tags/xxx 标签（三者 payload 格式一致）
        String ref = null;
        try {
            if (body != null && !body.isBlank()) {
                var node = mapper.readTree(body);
                if (node.has("ref")) ref = node.get("ref").asText();
            }
        } catch (Exception ignore) {}

        String defaultBranch = project.getDefaultBranch() == null || project.getDefaultBranch().isBlank()
                ? "main" : project.getDefaultBranch();
        String buildRef = resolveBuildRef(project, defaultBranch, ref);
        if (buildRef == null) {
            return ApiResult.ok(Map.of("triggered", false, "reason", "ref " + ref + " 不在触发范围，忽略"));
        }
        if (buildTaskRepository.existsByProjectIdAndStatusIn(
                project.getId(), List.of(TaskStatus.PENDING, TaskStatus.RUNNING))) {
            return ApiResult.ok(Map.of("triggered", false, "reason", "已有进行中的构建，跳过"));
        }

        // 构建目标：buildConfig.webhookTargets 优先，否则按项目类型取默认（定时构建同规则）
        List<String> targets = defaultTargetsOf(project);
        Object configured = project.getBuildConfig() == null ? null : project.getBuildConfig().get("webhookTargets");
        if (configured instanceof List<?> l && !l.isEmpty()) {
            targets = l.stream().map(String::valueOf).toList();
        }

        BuildTask task = buildService.start(project.getId(), buildRef, targets, BuildTrigger.WEBHOOK);
        log.info("Webhook 触发构建: project={} ref={} build={}", code, buildRef, task.getId());
        return ApiResult.ok(Map.of("triggered", true, "buildId", task.getId(), "branch", buildRef));
    }

    /**
     * 判定 ref 是否触发构建，返回实际检出的分支/标签名：
     * - 标签 push（refs/tags/x）：始终触发（打标签即发布意图），检出 tag 构建；
     * - 分支 push：buildConfig.webhookBranches 配置了分支列表则按列表放行，否则仅默认分支。
     */
    static String resolveBuildRef(Project project, String defaultBranch, String ref) {
        if (ref == null || ref.isBlank()) return defaultBranch; // 拿不到 ref（非标准 payload）按默认分支构建
        if (ref.startsWith("refs/tags/")) {
            String tag = ref.substring("refs/tags/".length());
            return tag.isBlank() ? null : tag;
        }
        String branch = ref.startsWith("refs/heads/") ? ref.substring("refs/heads/".length()) : ref;
        Object configured = project.getBuildConfig() == null ? null : project.getBuildConfig().get("webhookBranches");
        if (configured instanceof List<?> l && !l.isEmpty()) {
            return l.stream().map(String::valueOf).anyMatch(b -> b.equals(branch)) ? branch : null;
        }
        return branch.equals(defaultBranch) ? branch : null;
    }

    /** 默认构建目标（webhook 与定时构建共用）：webhookTargets 未配置时按项目类型取 */
    static List<String> defaultTargetsOf(Project project) {
        return switch (project.getType()) {
            case FRONTEND -> List.of(BuildTarget.FRONTEND_TAR.name());
            case BACKEND -> List.of(BuildTarget.BACKEND_JAR.name());
            case FULL -> List.of(BuildTarget.FRONTEND_TAR.name(), BuildTarget.BACKEND_JAR.name());
        };
    }

    private static boolean hmacVerify(String secret, String body, String received) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "sha256=" + HexFormat.of().formatHex(
                    mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    received.trim().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean constantEq(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
