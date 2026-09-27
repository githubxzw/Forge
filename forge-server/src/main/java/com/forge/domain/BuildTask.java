package com.forge.domain;

import jakarta.persistence.*;
import com.forge.common.Converters;
import org.hibernate.annotations.UuidGenerator;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "build_task")
public class BuildTask {
    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @ManyToOne(optional = false)
    private Project project;

    private String branch;

    /** SQLite 保留字，列名映射为 commit_sha */
    @Column(name = "commit_sha")
    private String commit;

    @Convert(converter = Converters.StrListConverter.class)
    @Column(columnDefinition = "text")
    private List<String> targets;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TaskStatus status = TaskStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", length = 16)
    private BuildTrigger trigger = BuildTrigger.MANUAL;

    /** 本次构建生成的统一版本号 */
    private String version;

    private String logPath;
    private String message;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
