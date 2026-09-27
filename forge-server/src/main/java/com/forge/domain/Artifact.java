package com.forge.domain;

import jakarta.persistence.*;
import com.forge.common.Converters;
import org.hibernate.annotations.UuidGenerator;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Entity
@Table(name = "artifact")
public class Artifact {
    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @ManyToOne(optional = false)
    private Project project;

    @ManyToOne
    private BuildTask buildTask;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ArtifactType type;

    @Column(nullable = false, length = 128)
    private String version;

    /**
     * 文件制品：磁盘相对路径；镜像制品：name:tag
     */
    @Column(nullable = false, columnDefinition = "text")
    private String ref;

    private String checksum;
    private Long size;

    /** 附加元数据：IMAGE/CHART 携带 {imageName, tag, sets[]} */
    @Convert(converter = Converters.MapConverter.class)
    @Column(columnDefinition = "text")
    private Map<String, Object> extra;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
