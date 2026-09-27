package com.forge.setting;

import com.forge.common.ApiException;
import com.forge.common.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingController {
    private final SettingService settingService;
    private final NotifyService notifyService;

    @GetMapping
    public ApiResult<Map<String, String>> all() {
        return ApiResult.ok(settingService.getAll());
    }

    @PutMapping
    public ApiResult<Void> update(@RequestBody Map<String, String> values) {
        settingService.update(values);
        return ApiResult.ok();
    }

    /** 发送测试通知，验证机器人配置 */
    @PostMapping("/notify-test")
    public ApiResult<Void> notifyTest() {
        String err = notifyService.send("Forge 测试通知",
                "如果你收到这条消息，说明通知渠道配置正确，部署结果将推送到这里。");
        if (err != null) throw new ApiException(400, "测试通知发送失败: " + err);
        return ApiResult.ok();
    }
}
