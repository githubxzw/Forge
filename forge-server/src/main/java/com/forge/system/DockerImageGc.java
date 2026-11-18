package com.forge.system;

import com.forge.domain.Artifact;
import com.forge.domain.ArtifactRepository;
import com.forge.domain.ArtifactType;
import com.forge.infra.log.LogSink;
import com.forge.infra.process.ProcessRunner;
import com.forge.setting.SettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 本地 Docker 镜像回收（每日 03:40，dockerImageGc=false 关闭）：
 * 1. docker image prune -f 清理悬挂层（构建中间产物，安全）；
 * 2. 删除不被任何 IMAGE 制品引用的本地镜像——Forge 每次构建都产出新 tag，旧版本会无限堆积。
 * 镜像在远端主机（docker save|load / registry pull）的副本不受影响。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DockerImageGc {
    private final ArtifactRepository artifactRepository;
    private final SettingService settingService;

    @Scheduled(cron = "0 40 3 * * *")
    public void run() {
        if (!"true".equalsIgnoreCase(settingService.get("dockerImageGc"))) return;
        try {
            ProcessRunner.run(List.of("docker", "image", "prune", "-f"), null, null, LogSink.NOOP, null, 120);

            List<String> all = ProcessRunner.capture(
                    List.of("docker", "images", "--format", "{{.Repository}}:{{.Tag}}"), null, 60)
                    .lines().map(String::trim).filter(s -> !s.isBlank() && !s.endsWith(":<none>")).toList();
            Set<String> referenced = referencedRefs();
            List<String> unused = unreferenced(all, referenced);
            for (String ref : unused) {
                try {
                    ProcessRunner.run(List.of("docker", "rmi", ref), null, null, LogSink.NOOP, null, 120);
                } catch (Exception ignore) {
                    // 镜像被容器占用等删除失败属正常，跳过即可
                }
            }
            if (!unused.isEmpty()) log.info("镜像 GC 完成：清理未引用镜像 {} 个", unused.size());
        } catch (Exception e) {
            // docker 不存在/守护进程未启动时静默跳过（GC 是尽力而为的清理，不能报错刷屏）
            log.debug("镜像 GC 跳过: {}", e.getMessage());
        }
    }

    /** 未被制品引用的镜像列表（纯函数便于单测） */
    static List<String> unreferenced(List<String> allImages, Set<String> referencedRefs) {
        return allImages.stream().filter(ref -> !referencedRefs.contains(ref)).toList();
    }

    /** 所有 IMAGE 制品的 ref 集合（含 registry 前缀与否以入库 ref 为准） */
    Set<String> referencedRefs() {
        Set<String> refs = new HashSet<>();
        for (Artifact a : artifactRepository.findByType(ArtifactType.IMAGE)) {
            if (a.getRef() != null && !a.getRef().isBlank()) refs.add(a.getRef());
        }
        return refs;
    }
}
