package com.forge.deploy;

import com.forge.common.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 持续监控状态（VIEWER 可读） */
@RestController
@RequestMapping("/api/monitor")
@RequiredArgsConstructor
public class MonitorController {
    private final HealthMonitor healthMonitor;

    @GetMapping("/status")
    public ApiResult<List<Map<String, Object>>> status() {
        return ApiResult.ok(healthMonitor.status());
    }
}
