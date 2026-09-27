package com.forge.build;

import com.forge.common.ApiException;
import com.forge.domain.Artifact;
import com.forge.domain.ArtifactType;
import com.forge.domain.BuildTask;
import com.forge.domain.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class ArtifactService {
    private final com.forge.domain.ArtifactRepository artifactRepository;
    private final com.forge.config.ForgeProperties props;

    public Artifact attachAndSave(Artifact artifact, Project project, BuildTask task) {
        artifact.setProject(project);
        artifact.setBuildTask(task);
        return artifactRepository.save(artifact);
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

    public void delete(Artifact artifact) {
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
