package com.forge.build;

import com.forge.common.ApiResult;
import com.forge.domain.BuildTask;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/builds")
@RequiredArgsConstructor
public class BuildController {
    private final BuildService buildService;
    private final com.forge.domain.BuildTaskRepository buildTaskRepository;

    @Data
    public static class StartReq {
        private String projectId;
        private String branch;
        private List<String> targets;
    }

    @PostMapping
    public ApiResult<BuildTask> start(@RequestBody StartReq req) {
        return ApiResult.ok(buildService.start(req.getProjectId(), req.getBranch(), req.getTargets()));
    }

    @GetMapping
    public ApiResult<Page<BuildTask>> list(@RequestParam(required = false) String projectId,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        Page<BuildTask> result = projectId == null || projectId.isBlank()
                ? buildTaskRepository.findByOrderByCreatedAtDesc(PageRequest.of(page, size))
                : buildTaskRepository.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(page, size));
        return ApiResult.ok(result);
    }

    @GetMapping("/{id}")
    public ApiResult<BuildTask> get(@PathVariable String id) {
        return ApiResult.ok(buildTaskRepository.findById(id).orElseThrow(() -> com.forge.common.ApiException.notFound("构建任务")));
    }

    @PostMapping("/{id}/cancel")
    public ApiResult<Boolean> cancel(@PathVariable String id) {
        return ApiResult.ok(buildService.cancel(id));
    }

    @GetMapping("/{id}/log")
    public ResponseEntity<String> log(@PathVariable String id) throws Exception {
        var task = buildTaskRepository.findById(id).orElseThrow(() -> com.forge.common.ApiException.notFound("构建任务"));
        var path = java.nio.file.Path.of(task.getLogPath() == null ? "" : task.getLogPath());
        String content = Files.exists(path) ? Files.readString(path) : "(无日志)";
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=build-" + id + ".log")
                .body(content);
    }
}
