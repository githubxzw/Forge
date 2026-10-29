package com.forge.build;

import com.forge.common.ApiResult;
import com.forge.domain.BuildTask;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/builds")
@RequiredArgsConstructor
public class BuildController {
    private final BuildService buildService;
    private final com.forge.domain.BuildTaskRepository buildTaskRepository;
    private final com.forge.auth.TicketService ticketService;

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

    /** 日志下载：permitAll 路径，靠一次性 ticket 或 JWT 头鉴权（见 SecurityConfig），流式输出不驻堆 */
    @GetMapping("/{id}/log")
    public org.springframework.http.ResponseEntity<
            org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> log(
            @PathVariable String id, @RequestParam(required = false) String ticket) throws Exception {
        ticketService.requireAuthOrTicket(ticket);
        var task = buildTaskRepository.findById(id).orElseThrow(() -> com.forge.common.ApiException.notFound("构建任务"));
        var path = java.nio.file.Path.of(task.getLogPath() == null ? "" : task.getLogPath());
        return com.forge.common.StreamedLog.of(path, "build-" + id + ".log");
    }
}
