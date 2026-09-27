package com.forge.domain;

import jakarta.persistence.*;
import com.forge.common.Converters;
import org.hibernate.annotations.UuidGenerator;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Entity
@Table(name = "deployment")
public class Deployment {
    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @ManyToOne(optional = false)
    private Project project;

    @ManyToOne(optional = false)
    private Environment environment;

    @ManyToOne(optional = false)
    private Artifact artifact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TaskStatus status = TaskStatus.RUNNING;

    /** 回滚指向的 Deployment id（全新部署为 null） */
    private String rollbackOf;

    /** 结果：{durationMs, revision?, health?...} */
    @Convert(converter = Converters.MapConverter.class)
    @Column(columnDefinition = "text")
    private Map<String, Object> result;

    private String logPath;
    private String operator;
    private String message;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
