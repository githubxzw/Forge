package com.forge.auth;

import com.forge.common.ApiException;
import com.forge.common.ApiResult;
import com.forge.domain.AppUser;
import com.forge.domain.UserRepository;
import com.forge.domain.UserRole;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 用户管理（仅 ADMIN；路径级权限见 SecurityConfig） */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public ApiResult<List<Map<String, Object>>> list() {
        return ApiResult.ok(userRepository.findAll().stream()
                .map(u -> Map.<String, Object>of(
                        "id", u.getId(),
                        "username", u.getUsername(),
                        "role", u.roleOrDefault().name(),
                        "createdAt", String.valueOf(u.getCreatedAt())))
                .toList());
    }

    @Data
    public static class UserReq {
        @NotBlank private String username;
        @NotBlank private String password;
        private String role;
    }

    @PostMapping
    public ApiResult<Map<String, Object>> create(@Validated @RequestBody UserReq req) {
        if (userRepository.findByUsername(req.getUsername()).isPresent()) {
            throw new ApiException("用户名已存在: " + req.getUsername());
        }
        if (req.getPassword().length() < 6) throw new ApiException("密码长度至少 6 位");
        AppUser u = new AppUser();
        u.setUsername(req.getUsername());
        u.setPassword(passwordEncoder.encode(req.getPassword()));
        u.setRole(parseRole(req.getRole()));
        u = userRepository.save(u);
        return ApiResult.ok(Map.of("id", u.getId(), "username", u.getUsername(), "role", u.roleOrDefault().name()));
    }

    /** 更新角色 / 重置密码（role 与 newPassword 至少给一个） */
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable String id, @RequestBody Map<String, String> body) {
        AppUser u = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("用户"));
        if (body.containsKey("role")) {
            UserRole role = parseRole(body.get("role"));
            if (u.roleOrDefault() == UserRole.ADMIN && role != UserRole.ADMIN) {
                assertNotLastAdmin(u); // 不允许把最后一个管理员降级，否则系统将无人可管
            }
            u.setRole(role);
        }
        String newPwd = body.get("newPassword");
        if (newPwd != null && !newPwd.isBlank()) {
            if (newPwd.length() < 6) throw new ApiException("密码长度至少 6 位");
            u.setPassword(passwordEncoder.encode(newPwd));
            u.setPasswordChangedAt(java.time.LocalDateTime.now()); // 重置后该用户现有登录态全部失效
        }
        userRepository.save(u);
        return ApiResult.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable String id) {
        AppUser u = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("用户"));
        assertNotLastAdmin(u);
        userRepository.delete(u);
        return ApiResult.ok();
    }

    private void assertNotLastAdmin(AppUser target) {
        long admins = userRepository.findAll().stream().filter(x -> x.roleOrDefault() == UserRole.ADMIN).count();
        if (admins <= 1 && target.roleOrDefault() == UserRole.ADMIN) {
            throw new ApiException("不能删除/降级最后一个管理员");
        }
    }

    private static UserRole parseRole(String role) {
        if (role == null || role.isBlank()) return UserRole.VIEWER;
        try {
            return UserRole.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException("未知角色: " + role + "（可选 ADMIN/OPERATOR/VIEWER）");
        }
    }
}
