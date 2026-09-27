package com.forge.domain;

import jakarta.persistence.*;
import com.forge.common.Converters;
import org.hibernate.annotations.UuidGenerator;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "credential")
public class Credential {
    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 64)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CredentialKind kind;

    /** AES-GCM 密文（Base64 IV + 密文），内容为 JSON：data 的序列化 */
    @Column(nullable = false, columnDefinition = "text")
    private String cipherText;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
