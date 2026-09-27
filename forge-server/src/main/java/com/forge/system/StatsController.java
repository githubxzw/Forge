package com.forge.system;

import com.forge.common.ApiResult;
import com.forge.config.ForgeProperties;
import com.forge.domain.BuildTask;
import com.forge.domain.BuildTaskRepository;
import com.forge.domain.Deployment;
import com.forge.domain.DeploymentRepository;
import com.forge.domain.TaskStatus;
import com.forge.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {
    private final com.forge.domain.ProjectRepository projectRepository;
    private final BuildTaskRepository buildTaskRepository;
    private final DeploymentRepository deploymentRepository;
    private final com.forge.domain.ArtifactRepository artifactRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ForgeProperties props;

    @GetMapping
    public ApiResult<Map<String, Object>> stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("projects", projectRepository.count());
        data.put("builds", buildTaskRepository.count());
        data.put("artifacts", artifactRepository.count());
        data.put("deployments", deploymentRepository.count());
        data.put("recentBuilds", buildTaskRepository.findByOrderByCreatedAtDesc(org.springframework.data.domain.PageRequest.of(0, 5)).getContent());
        data.put("recentDeployments", deploymentRepository.findTop10ByOrderByCreatedAtDesc().stream().limit(5).toList());

        // 近 7 天构建/部署成功率
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        var builds7 = buildTaskRepository.findByCreatedAtAfter(weekAgo);
        var deploys7 = deploymentRepository.findByCreatedAtAfter(weekAgo);
        data.put("builds7", Map.of(
                "total", builds7.size(),
                "ok", builds7.stream().filter(b -> b.getStatus() == TaskStatus.SUCCESS).count()));
        data.put("deploys7", Map.of(
                "total", deploys7.size(),
                "ok", deploys7.stream().filter(d -> d.getStatus() == TaskStatus.SUCCESS).count()));

        // 管理员是否仍在使用默认密码（公网部署前必须修改）
        data.put("defaultPassword", isDefaultPassword());
        return ApiResult.ok(data);
    }

    private boolean isDefaultPassword() {
        return userRepository.findByUsername(props.getAdmin().getUsername())
                .map(u -> passwordEncoder.matches("admin123", u.getPassword()))
                .orElse(false);
    }
}
