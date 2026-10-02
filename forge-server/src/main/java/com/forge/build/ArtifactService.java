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
                Path parent = file.getParent();
                if (parent != null) TarUtils.deleteDir(parent);
            } catch (IOException ignore) {}
        }
        artifactRepository.delete(artifact);
    }
}
