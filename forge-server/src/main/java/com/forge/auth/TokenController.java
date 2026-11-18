package com.forge.auth;

import com.forge.common.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** API Token 管理（仅 JWT 登录后可用；令牌本身用于脚本调 API） */
@RestController
@RequestMapping("/api/tokens")
@RequiredArgsConstructor
public class TokenController {
    private final ApiTokenService apiTokenService;

    @GetMapping
    public ApiResult<List<com.forge.domain.ApiToken>> list() {
        return ApiResult.ok(apiTokenService.list());
    }

    /** 创建：响应里 token 字段是完整明文，仅此一次 */
    @PostMapping
    public ApiResult<com.forge.domain.ApiToken> create(@RequestBody Map<String, String> body) {
        return ApiResult.ok(apiTokenService.create(body.get("name")));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable String id) {
        apiTokenService.delete(id);
        return ApiResult.ok();
    }
}
