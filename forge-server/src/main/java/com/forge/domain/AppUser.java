package com.forge.domain;

import jakarta.persistence.*;
import com.forge.common.Converters;
import org.hibernate.annotations.UuidGenerator;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "app_user")
public class AppUser {
    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    /** bcrypt */
    @Column(nullable = false)
    private String password;

    /** 角色；历史行可能为 null（升级前创建），取值统一走 roleOrDefault() */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private UserRole role;

    public UserRole roleOrDefault() {
        return role == null ? UserRole.ADMIN : role;
    }

    /** 最近一次修改密码的时间：该时间之前签发的 JWT 全部失效 */
    private LocalDateTime passwordChangedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
