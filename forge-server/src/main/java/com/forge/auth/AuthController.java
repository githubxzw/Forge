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
    private final TicketService ticketService;

    /** 登录防爆破：同一用户名连续失败 5 次锁定 10 分钟（内存态，重启即清零） */
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_MS = 10 * 60 * 1000L;
    private static final int MAX_TRACKED_USERS = 10_000;
    private static final java.util.Map<String, long[]> ATTEMPTS = new java.util.concurrent.ConcurrentHashMap<>();
    /** 用户不存在时也执行一次同代价的 bcrypt 比对，拉平响应时间，堵住用户名枚举侧信道 */
    private static final String DUMMY_HASH =
            "$2a$10$MF17SWsMGEw5nl1CkPhWIu5gvkAtrqiQ3NhgninxrLcGfS16daAZa"; // bcrypt("forge-dummy")

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
        AppUser user = userRepository.findByUsername(username).orElse(null);
        boolean matched = user != null && passwordEncoder.matches(req.getPassword(), user.getPassword());
        if (!matched) {
            if (user == null) passwordEncoder.matches(req.getPassword(), DUMMY_HASH); // 拉平耗时
            recordFailure(username, attempt);
            throw new ApiException(401, "用户名或密码错误");
        }
        ATTEMPTS.remove(username);
        return ApiResult.ok(Map.of("token", jwtService.issue(user.getUsername(), user.roleOrDefault().name()),
                "username", user.getUsername(), "role", user.roleOrDefault().name()));
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
        // 上限保护：恶意用大量随机用户名打登录接口时不至于无界增长
        if (ATTEMPTS.size() < MAX_TRACKED_USERS || ATTEMPTS.containsKey(username)) {
            ATTEMPTS.put(username, attempt);
        }
    }

    @GetMapping("/me")
    public ApiResult<Map<String, String>> me() {
        String name = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        var auth = SecurityContextHolder.getContext().getAuthentication();
        String role = auth.getAuthorities().stream()
                .map(String::valueOf)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst().orElse("VIEWER");
        return ApiResult.ok(Map.of("username", name, "role", role));
    }

    /** 签发一次性短效 ticket（需 JWT 头鉴权）：供 WS 握手与日志下载等无法带头的通道使用 */
    @GetMapping("/ticket")
    public ApiResult<Map<String, String>> ticket() {
        return ApiResult.ok(Map.of("ticket", ticketService.issue()));
    }

    @PostMapping("/password")
    public ApiResult<Void> changePassword(@Validated @RequestBody Map<String, String> body) {
        String name = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        AppUser user = userRepository.findByUsername(name).orElseThrow();
        String oldPwd = body.get("oldPassword");
        String newPwd = body.get("newPassword");
        if (oldPwd == null || oldPwd.isBlank()) throw new ApiException("原密码不能为空");
        if (newPwd == null || newPwd.length() < 6) throw new ApiException("新密码长度至少 6 位");
        if (!passwordEncoder.matches(oldPwd, user.getPassword())) throw new ApiException("原密码错误");
        user.setPassword(passwordEncoder.encode(newPwd));
        user.setPasswordChangedAt(java.time.LocalDateTime.now()); // 使之前签发的 JWT 全部失效
        userRepository.save(user);
        return ApiResult.ok();
    }
}
