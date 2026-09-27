package com.forge.auth;

import com.forge.common.ApiException;
import com.forge.common.ApiResult;
import com.forge.domain.AppUser;
import com.forge.domain.UserRepository;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /** 登录防爆破：同一用户名连续失败 5 次锁定 10 分钟（内存态，重启即清零） */
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_MS = 10 * 60 * 1000L;
    private static final java.util.Map<String, long[]> ATTEMPTS = new java.util.concurrent.ConcurrentHashMap<>();

    @Data
    public static class LoginReq {
        @NotBlank private String username;
        @NotBlank private String password;
    }

    @PostMapping("/login")
    public ApiResult<Map<String, String>> login(@Validated @RequestBody LoginReq req) {
        String username = req.getUsername();
        long[] attempt = ATTEMPTS.get(username);
        long now = System.currentTimeMillis();
        if (attempt != null && attempt[1] > now) {
            long minutes = (attempt[1] - now) / 60000 + 1;
            throw new ApiException(429, "失败次数过多，账号已临时锁定，请 " + minutes + " 分钟后重试");
        }
        AppUser user = userRepository.findByUsername(username)
                .filter(u -> passwordEncoder.matches(req.getPassword(), u.getPassword()))
                .orElse(null);
        if (user == null) {
            recordFailure(username, attempt);
            throw new ApiException(401, "用户名或密码错误");
        }
        ATTEMPTS.remove(username);
        return ApiResult.ok(Map.of("token", jwtService.issue(user.getUsername()), "username", user.getUsername()));
    }

    private void recordFailure(String username, long[] attempt) {
        long now = System.currentTimeMillis();
        // attempt[0]=连续失败次数, attempt[1]=锁定截止时间(0=未锁定)；仅当锁定已过期才重新计数
        if (attempt == null) attempt = new long[]{0, 0};
        else if (attempt[1] != 0 && attempt[1] < now) attempt = new long[]{0, 0};
        attempt[0]++;
        if (attempt[0] >= MAX_ATTEMPTS) {
            attempt[1] = now + LOCK_MS;
            attempt[0] = 0;
        }
        ATTEMPTS.put(username, attempt);
    }

    @GetMapping("/me")
    public ApiResult<Map<String, String>> me() {
        String name = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResult.ok(Map.of("username", name));
    }

    @PostMapping("/password")
    public ApiResult<Void> changePassword(@Validated @RequestBody Map<String, String> body) {
        String name = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        AppUser user = userRepository.findByUsername(name).orElseThrow();
        String oldPwd = body.get("oldPassword");
        String newPwd = body.get("newPassword");
        if (newPwd == null || newPwd.length() < 6) throw new ApiException("新密码长度至少 6 位");
        if (!passwordEncoder.matches(oldPwd, user.getPassword())) throw new ApiException("原密码错误");
        user.setPassword(passwordEncoder.encode(newPwd));
        userRepository.save(user);
        return ApiResult.ok();
    }
}
