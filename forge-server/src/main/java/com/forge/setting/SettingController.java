package com.forge.setting;

import com.forge.common.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingController {
    private final SettingService settingService;

    @GetMapping
    public ApiResult<Map<String, String>> all() {
        return ApiResult.ok(settingService.getAll());
    }

    @PutMapping
    public ApiResult<Void> update(@RequestBody Map<String, String> values) {
        settingService.update(values);
        return ApiResult.ok();
    }
}
