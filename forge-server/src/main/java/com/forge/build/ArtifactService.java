package com.forge.build;

import com.forge.common.ApiException;
import com.forge.domain.Artifact;
import com.forge.domain.ArtifactType;
import com.forge.domain.BuildTask;
import com.forge.domain.Project;
import com.forge.setting.SettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ArtifactService {
    private final com.forge.domain.ArtifactRepository artifactRepository;
    private final com.forge.domain.DeploymentRepository deploymentRepository;
    private final com.forge.config.ForgeProperties props;
    private final SettingService settingService;

    public Artifact attachAndSave(Artifact artifact, Project project, BuildTask task) {
        artifact.setProject(project);
        artifact.setBuildTask(task);
        Artifact saved = artifactRepository.save(artifact);
        cleanupByRetention(project.getId(), artifact.getType());
        return saved;
    }

    /** 保留策略：每项目每类型只保留最近 N 个（未被部署引用的才清理），keepArtifacts=0 表示永久 */
    public void cleanupByRetention(String projectId, ArtifactType type) {
        int keep;
        try {
            keep = Integer.parseInt(settingService.get("keepArtifacts"));
        } catch (Exception e) {
            return;
        }
        if (keep <= 0) return;
        List<Artifact> all = artifactRepository.findByProjectIdAndTypeOrderByCreatedAtDesc(projectId, type);
        for (int i = keep; i < all.size(); i++) {
            Artifact old = all.get(i);
            if (deploymentRepository.existsByArtifactId(old.getId())) continue;
            try {
                delete(old);
            } catch (Exception ignore) {}
        }
    }

    public Artifact require(String id) {
        return artifactRepository.findById(id).orElseThrow(() -> ApiException.notFound("制品"));
    }

    /** 文件制品的磁盘绝对路径；镜像制品返回 null */
    public Path resolveFile(Artifact artifact) {
        if (artifact.getType() == ArtifactType.IMAGE) return null;
        Path file = props.artifactPath().resolve(artifact.getRef());
        if (!Files.exists(file)) throw new ApiException(404, "制品文件丢失: " + artifact.getRef());
        return file;
    }

    /**
     * 校验制品文件 sha256 与入库值一致——部署上传前与下载出库前调用，磁盘损坏/被篡改的制品拒绝流出。
     * 镜像制品（仅 tag 引用）与历史无校验和的制品跳过，返回是否实际执行了校验。
     */
    public boolean verifyChecksum(Artifact artifact) {
        if (artifact.getType() == ArtifactType.IMAGE) return false;
        if (artifact.getChecksum() == null || artifact.getChecksum().isBlank()) return false;
        Path file = resolveFile(artifact);
        String actual = TarUtils.sha256(file);
        if (!actual.equalsIgnoreCase(artifact.getChecksum())) {
            throw new ApiException(500, "制品 sha256 校验失败（文件可能损坏或被篡改）: " + artifact.getRef());
        }
        return true;
    }

    /** 已被部署记录引用的制品不允许删除（外键 + 可追溯性） */
    public void assertNotReferenced(Artifact artifact) {
        if (deploymentRepository.existsByArtifactId(artifact.getId())) {
            throw new ApiException("该制品已被部署记录引用，无法删除");
        }
    }

    public void delete(Artifact artifact) {
        assertNotReferenced(artifact);
        if (artifact.getType() != ArtifactType.IMAGE) {
            try {
                Path file = props.artifactPath().resolve(artifact.getRef());
                Files.deleteIfExists(file);
                // 父目录是同项目同类型全部制品的共享目录，仅当已空时才删除
                Path parent = file.getParent();
                if (parent != null) {
                    try (var entries = Files.list(parent)) {
                        if (entries.findAny().isEmpty()) Files.delete(parent);
                    }
                }
            } catch (IOException ignore) {}
        }
        artifactRepository.delete(artifact);
    }
}
