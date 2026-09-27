package com.forge.project;

import com.forge.common.ApiResult;
import com.forge.domain.Project;
import com.forge.domain.ProjectType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {
    private final ProjectService projectService;

    @GetMapping
    public ApiResult<List<Project>> list() {
        return ApiResult.ok(projectService.list());
    }

    @GetMapping("/{id}")
    public ApiResult<Project> get(@PathVariable String id) {
        return ApiResult.ok(projectService.require(id));
    }

    @PostMapping
    public ApiResult<Project> create(@RequestBody Project project) {
        return ApiResult.ok(projectService.create(project));
    }

    @PutMapping("/{id}")
    public ApiResult<Project> update(@PathVariable String id, @RequestBody Project project) {
        return ApiResult.ok(projectService.update(id, project));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable String id) {
        projectService.delete(id);
        return ApiResult.ok();
    }

    @PutMapping("/{id}/webhook-secret")
    public ApiResult<String> regenerateWebhookSecret(@PathVariable String id) {
        return ApiResult.ok(projectService.regenerateWebhookSecret(id));
    }
}
