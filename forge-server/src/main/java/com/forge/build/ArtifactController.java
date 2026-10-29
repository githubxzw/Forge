package com.forge.build;

import com.forge.common.ApiException;
import com.forge.common.ApiResult;
import com.forge.domain.Artifact;
import com.forge.domain.ArtifactType;
import com.forge.infra.process.ProcessRunner;
import com.forge.setting.SettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/artifacts")
@RequiredArgsConstructor
public class ArtifactController {
    private final com.forge.domain.ArtifactRepository artifactRepository;
    private final ArtifactService artifactService;
    private final SettingService settingService;
    private final DockerBuilder dockerBuilder;

    @GetMapping
    public ApiResult<Page<Artifact>> list(@RequestParam(required = false) String projectId,
                                          @RequestParam(required = false) String type,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        Page<Artifact> result;
        ArtifactType typeEnum = null;
        if (type != null && !type.isBlank()) {
            try {
                typeEnum = ArtifactType.valueOf(type);
            } catch (IllegalArgumentException e) {
                throw new ApiException("未知制品类型: " + type);
            }
        }
        if (projectId != null && !projectId.isBlank() && typeEnum != null) {
            result = artifactRepository.findByProjectIdAndTypeOrderByCreatedAtDesc(
                    projectId, typeEnum, PageRequest.of(page, size));
        } else if (projectId != null && !projectId.isBlank()) {
            result = artifactRepository.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(page, size));
        } else {
            result = artifactRepository.findByOrderByCreatedAtDesc(PageRequest.of(page, size));
        }
        return ApiResult.ok(result);
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable String id) {
        Artifact artifact = artifactService.require(id);
        artifactService.delete(artifact);
        return ApiResult.ok();
    }

    /** 批量删除：被部署引用的制品自动跳过 */
    @PostMapping("/batch-delete")
    public ApiResult<Map<String, Object>> batchDelete(@RequestBody Map<String, List<String>> body) {
        List<String> ids = body.getOrDefault("ids", List.of());
        int deleted = 0;
        List<String> skipped = new java.util.ArrayList<>();
        for (String id : ids) {
            try {
                artifactService.delete(artifactService.require(id));
                deleted++;
            } catch (Exception e) {
                skipped.add(id);
            }
        }
        return ApiResult.ok(Map.of("deleted", deleted, "skipped", skipped.size()));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<FileSystemResource> download(@PathVariable String id) {
        Artifact artifact = artifactService.require(id);
        Path file = artifactService.resolveFile(artifact);
        if (file == null) throw new ApiException("镜像制品不支持下载，请使用 docker pull");
        String filename = file.getFileName().toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(file));
    }

    /** 推送镜像到 registry */
    @PostMapping("/{id}/push")
    public ApiResult<Void> push(@PathVariable String id) {
        Artifact artifact = artifactService.require(id);
        if (artifact.getType() != ArtifactType.IMAGE) throw new ApiException("仅镜像制品支持推送");
        String registry = settingService.get("registry");
        if (registry.isBlank()) throw new ApiException("请先在系统设置中配置镜像仓库");
        String user = settingService.get("registryUsername");
        String password = settingService.registryPasswordPlain();
        if (!user.isBlank() && !password.isBlank()) {
            List<String> login = new java.util.ArrayList<>(List.of("docker", "login", "-u", user, "--password-stdin"));
            if (!registry.isBlank()) login.add(registry);
            ProcessRunner.runWithStdin(login, password, 120);
        }
        ProcessRunner.run(List.of("docker", "push", artifact.getRef()), null, null,
                com.forge.infra.log.LogSink.NOOP, null, 1800);
        return ApiResult.ok();
    }
}
