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

        // 解析 push 分支（GitHub/Gitee/GitLab 均为 refs/heads/xxx）
        String branch = null;
        try {
            if (body != null && !body.isBlank()) {
                var node = mapper.readTree(body);
                if (node.has("ref")) {
                    branch = node.get("ref").asText().replaceFirst("^refs/heads/", "");
                }
            }
        } catch (Exception ignore) {}

        String defaultBranch = project.getDefaultBranch() == null || project.getDefaultBranch().isBlank()
                ? "main" : project.getDefaultBranch();
        if (branch != null && !branch.isBlank() && !branch.equals(defaultBranch)) {
            return ApiResult.ok(Map.of("triggered", false, "reason", "分支 " + branch + " 不匹配，忽略"));
        }
        if (buildTaskRepository.existsByProjectIdAndStatusIn(
                project.getId(), List.of(TaskStatus.PENDING, TaskStatus.RUNNING))) {
            return ApiResult.ok(Map.of("triggered", false, "reason", "已有进行中的构建，跳过"));
        }

        // 构建目标：buildConfig.webhookTargets 优先，否则按项目类型取默认
        List<String> targets = defaultTargets(project);
        Object configured = project.getBuildConfig() == null ? null : project.getBuildConfig().get("webhookTargets");
        if (configured instanceof List<?> l && !l.isEmpty()) {
            targets = l.stream().map(String::valueOf).toList();
        }

        BuildTask task = buildService.start(project.getId(), defaultBranch, targets, BuildTrigger.WEBHOOK);
        log.info("Webhook 触发构建: project={} branch={} build={}", code, defaultBranch, task.getId());
        return ApiResult.ok(Map.of("triggered", true, "buildId", task.getId(), "branch", defaultBranch));
    }

    private List<String> defaultTargets(Project project) {
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
