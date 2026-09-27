package com.forge.domain;

import jakarta.persistence.*;
import com.forge.common.Converters;
import org.hibernate.annotations.UuidGenerator;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Entity
@Table(name = "environment")
public class Environment {
    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 64)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EnvKind kind;

    /**
     * SSH:    {host, port, username, credentialId, deployDir, startCommand?, stopCommand?, systemdService?}
     * DOCKER: {host?, port?, username?, credentialId?, composeDir}
     * K8S:    {kubeconfigCredentialId, namespace}
     */
    @Convert(converter = Converters.MapConverter.class)
    @Column(columnDefinition = "text")
    private Map<String, Object> connConfig;

    /** 环境变量注入；key 以 helm. 开头的项会作为 --set 传给 helm */
    @Convert(converter = Converters.StrMapConverter.class)
    @Column(columnDefinition = "text")
    private Map<String, String> vars;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
