package com.forge.deploy;

import com.forge.common.ApiResult;
import com.forge.domain.Deployment;
import com.forge.domain.DeploymentRepository;
import com.forge.domain.TaskStatus;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/deployments")
@RequiredArgsConstructor
public class DeploymentController {
    private final DeployService deployService;
    private final DeploymentRepository deploymentRepository;

    @Data
    public static class StartReq {
        private String projectId;
        private String envId;
        private String artifactId;
    }

    @PostMapping
    public ApiResult<Deployment> start(@RequestBody StartReq req) {
        return ApiResult.ok(deployService.start(req.getProjectId(), req.getEnvId(), req.getArtifactId()));
    }

    /** 批量顺序部署：同一环境逐个部署，前序失败即中止 */
    @PostMapping("/batch")
    public ApiResult<java.util.List<Deployment>> batch(@RequestBody BatchReq req) {
        return ApiResult.ok(deployService.startBatch(req.getEnvId(), req.getItems()));
    }

    @Data
    public static class BatchReq {
        private String envId;
        private java.util.List<com.forge.deploy.DeployService.BatchItem> items;
    }

    @PostMapping("/{id}/rollback")
    public ApiResult<Deployment> rollback(@PathVariable String id) {
        return ApiResult.ok(deployService.rollback(id));
    }

    /** 回滚预览：告知将回退到哪个版本（确认框展示用） */
    @GetMapping("/{id}/rollback-preview")
    public ApiResult<Map<String, Object>> rollbackPreview(@PathVariable String id) {
        return ApiResult.ok(deployService.rollbackPreview(id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResult<Boolean> cancel(@PathVariable String id) {
        return ApiResult.ok(deployService.cancel(id));
    }

    @GetMapping
    public ApiResult<Page<Deployment>> list(@RequestParam(required = false) String projectId,
                                            @RequestParam(required = false) String envId,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        Page<Deployment> result;
        if (projectId != null && !projectId.isBlank() && envId != null && !envId.isBlank()) {
            result = deploymentRepository.findByProjectIdAndEnvironmentIdOrderByCreatedAtDesc(
                    projectId, envId, PageRequest.of(page, size));
        } else if (projectId != null && !projectId.isBlank()) {
            result = deploymentRepository.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(page, size));
        } else {
            result = deploymentRepository.findByOrderByCreatedAtDesc(PageRequest.of(page, size));
        }
        return ApiResult.ok(result);
    }

    @GetMapping("/{id}")
    public ApiResult<Deployment> get(@PathVariable String id) {
        return ApiResult.ok(deploymentRepository.findById(id)
                .orElseThrow(() -> com.forge.common.ApiException.notFound("部署记录")));
    }

    @GetMapping("/{id}/log")
    public org.springframework.http.ResponseEntity<String> log(@PathVariable String id) throws Exception {
        Deployment dep = deploymentRepository.findById(id)
                .orElseThrow(() -> com.forge.common.ApiException.notFound("部署记录"));
        Path path = Path.of(dep.getLogPath() == null ? "" : dep.getLogPath());
        String content = Files.exists(path) ? Files.readString(path) : "(无日志)";
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=deploy-" + id + ".log")
                .body(content);
    }
}
