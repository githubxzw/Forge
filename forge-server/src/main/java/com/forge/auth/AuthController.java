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

    @Data
    public static class LoginReq {
        @NotBlank private String username;
        @NotBlank private String password;
    }

    @PostMapping("/login")
    public ApiResult<Map<String, String>> login(@Validated @RequestBody LoginReq req) {
        AppUser user = userRepository.findByUsername(req.getUsername())
                .orElseThrow(() -> new ApiException(401, "用户名或密码错误"));
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new ApiException(401, "用户名或密码错误");
        }
        return ApiResult.ok(Map.of("token", jwtService.issue(user.getUsername()), "username", user.getUsername()));
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
