package com.forge.domain;

import jakarta.persistence.*;
import com.forge.common.Converters;
import org.hibernate.annotations.UuidGenerator;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Entity
@Table(name = "project")
public class Project {
    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProjectType type;

    @Column(nullable = false, length = 512)
    private String repoUrl;

    /** 私有仓库凭据（SSH_KEY 或 存 token 的 PASSWORD/TEXT），空=公开仓库 */
    private String gitCredentialId;

    /** Webhook 自动构建密钥（自动生成，可重新生成；空=未启用） */
    private String webhookSecret;

    private String defaultBranch;

    /** 技术栈描述，如 vue/npm、maven */
    private String stack;

    /** 构建配置：buildCommand/outputDir/dockerfilePath/chartPath/imageName/containerPort/replicas/ports/healthPath 等 */
    @Convert(converter = Converters.MapConverter.class)
    @Column(columnDefinition = "text")
    private Map<String, Object> buildConfig;

    /** 健康检查配置：{url | path+port, timeoutSec, retries} */
    @Convert(converter = Converters.MapConverter.class)
    @Column(columnDefinition = "text")
    private Map<String, Object> healthCheck;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
