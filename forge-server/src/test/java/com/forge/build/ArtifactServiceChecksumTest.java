package com.forge.build;

import com.forge.common.ApiException;
import com.forge.config.ForgeProperties;
import com.forge.domain.Artifact;
import com.forge.domain.ArtifactType;
import com.forge.setting.SettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** 制品 sha256 出库校验（P0-3：损坏/被篡改的制品在部署与下载前被拒绝） */
class ArtifactServiceChecksumTest {

    @TempDir
    Path dir;

    ArtifactService service;
    Path artifactRoot;

    @BeforeEach
    void setup() {
        artifactRoot = dir.resolve("artifacts");
        ForgeProperties props = new ForgeProperties();
        props.setArtifactDir(artifactRoot.toString());
        service = new ArtifactService(mock(com.forge.domain.ArtifactRepository.class),
                mock(com.forge.domain.DeploymentRepository.class), props, mock(SettingService.class));
    }

    @Test
    void matchingChecksumPasses() throws Exception {
        Path file = artifactRoot.resolve("demo/jar/demo-v1.jar");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "hello forge");
        Artifact a = new Artifact();
        a.setType(ArtifactType.JAR);
        a.setRef("demo/jar/demo-v1.jar");
        a.setChecksum(TarUtils.sha256(file));
        assertTrue(service.verifyChecksum(a));
    }

    @Test
    void corruptedFileRejected() throws Exception {
        Path file = artifactRoot.resolve("demo/jar/demo-v1.jar");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "original");
        Artifact a = new Artifact();
        a.setType(ArtifactType.JAR);
        a.setRef("demo/jar/demo-v1.jar");
        a.setChecksum(TarUtils.sha256(file));
        Files.writeString(file, "tampered"); // 落库后文件被改动
        ApiException e = assertThrows(ApiException.class, () -> service.verifyChecksum(a));
        assertTrue(e.getMessage().contains("sha256"));
    }

    @Test
    void imageAndLegacyArtifactsSkipped() {
        Artifact image = new Artifact();
        image.setType(ArtifactType.IMAGE);
        image.setRef("demo:1.0");
        assertFalse(service.verifyChecksum(image));

        Artifact legacy = new Artifact(); // 历史数据无校验和
        legacy.setType(ArtifactType.JAR);
        legacy.setRef("demo/jar/none.jar");
        assertFalse(service.verifyChecksum(legacy));
    }

    @Test
    void missingFileThrows() {
        Artifact a = new Artifact();
        a.setType(ArtifactType.JAR);
        a.setRef("demo/jar/missing.jar");
        a.setChecksum("deadbeef");
        assertThrows(ApiException.class, () -> service.verifyChecksum(a));
    }
}
