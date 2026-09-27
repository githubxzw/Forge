package com.forge.system;

import com.forge.common.ApiResult;
import com.forge.domain.BuildTask;
import com.forge.domain.BuildTaskRepository;
import com.forge.domain.Deployment;
import com.forge.domain.DeploymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping
    public ApiResult<Map<String, Object>> stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("projects", projectRepository.count());
        data.put("builds", buildTaskRepository.count());
        data.put("artifacts", artifactRepository.count());
        data.put("deployments", deploymentRepository.count());
        data.put("recentBuilds", buildTaskRepository.findByOrderByCreatedAtDesc(org.springframework.data.domain.PageRequest.of(0, 5)).getContent());
        data.put("recentDeployments", deploymentRepository.findTop10ByOrderByCreatedAtDesc().stream().limit(5).toList());
        return ApiResult.ok(data);
    }
}
