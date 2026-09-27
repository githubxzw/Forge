package com.forge.project;

import com.forge.common.ApiResult;
import com.forge.domain.Environment;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/environments")
@RequiredArgsConstructor
public class EnvironmentController {
    private final EnvironmentService environmentService;

    @GetMapping
    public ApiResult<List<Environment>> list() {
        return ApiResult.ok(environmentService.list());
    }

    @GetMapping("/{id}")
    public ApiResult<Environment> get(@PathVariable String id) {
        return ApiResult.ok(environmentService.require(id));
    }

    @PostMapping
    public ApiResult<Environment> create(@RequestBody Environment env) {
        return ApiResult.ok(environmentService.create(env));
    }

    @PutMapping("/{id}")
    public ApiResult<Environment> update(@PathVariable String id, @RequestBody Environment env) {
        return ApiResult.ok(environmentService.update(id, env));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable String id) {
        environmentService.delete(id);
        return ApiResult.ok();
    }

    @PostMapping("/{id}/test")
    public ApiResult<Map<String, Object>> test(@PathVariable String id) {
        return ApiResult.ok(environmentService.test(id));
    }
}
