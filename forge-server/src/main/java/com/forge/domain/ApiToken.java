package com.forge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

/** API 访问令牌：脚本/CI 调用 REST API 用。只存 sha256，明文仅创建时返回一次 */
@Data
@Entity
@Table(name = "api_token")
public class ApiToken {
    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 64)
    private String name;

    /** sha256(明文) 十六进制，唯一索引加速鉴权查找 */
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    /** 明文前 8 位，列表展示便于辨认 */
    @Column(nullable = false, length = 16)
    private String prefix;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime lastUsedAt;
}
