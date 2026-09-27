package com.forge.build;

import com.forge.common.ApiException;
import com.forge.domain.Artifact;
import com.forge.domain.ArtifactType;
import com.forge.domain.BuildTarget;
import com.forge.domain.ProjectType;
import com.forge.infra.process.ProcessRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 前端包构建：npm/pnpm/yarn install + build → dist.tar.gz */
@Component
public class FrontendBuilder implements Builder {

    @Override
    public BuildTarget target() { return BuildTarget.FRONTEND_TAR; }

    @Override
    public Artifact build(BuildContext ctx) {
        Path src = ctx.getSrcDir();
        if (!Files.exists(src.resolve("package.json"))) {
            throw new ApiException("未找到 package.json，无法构建前端包");
        }
        String pm = detectPackageManager(src);
        ctx.getLog().line("[forge] 前端构建，包管理器: " + pm);

        // 1. 安装依赖
        String registry = ctx.setting("npmRegistry");
        List<String> install = new ArrayList<>();
        boolean hasLock = switch (pm) {
            case "pnpm" -> Files.exists(src.resolve("pnpm-lock.yaml"));
            case "yarn" -> Files.exists(src.resolve("yarn.lock"));
            default -> Files.exists(src.resolve("package-lock.json"));
        };
        switch (pm) {
            case "pnpm" -> {
                install.add("pnpm");
                install.add("install");
            }
            case "yarn" -> {
                install.add("yarn");
                install.add("install");
            }
            default -> {
                install.add("npm");
                if (hasLock) install.add("ci");
                else install.add("install");
            }
        }
        if (registry != null && !registry.isBlank() && !"https://registry.npmjs.org".equals(registry)) {
            install.add("--registry=" + registry);
        }
        ProcessRunner.run(install, src, ctx.getEnv(), ctx.getLog(), null, 1800);

        // 2. 构建（命令可覆盖）
        List<String> buildCmd = new ArrayList<>();
        String override = ctx.config("buildCommand");
        if (override != null && !override.isBlank()) {
            buildCmd.addAll(List.of(override.trim().split("\\s+")));
        } else {
            buildCmd.addAll(List.of(pm, "run", "build"));
        }
        ProcessRunner.run(buildCmd, src, ctx.getEnv(), ctx.getLog(), null, 3600);

        // 3. 定位产物目录并打包
        String outputDir = ctx.config("outputDir");
        if (outputDir == null || outputDir.isBlank()) {
            outputDir = Files.exists(src.resolve("dist")) ? "dist" : "build";
        }
        Path dist = src.resolve(outputDir);
        if (!Files.isDirectory(dist)) throw new ApiException("构建产物目录不存在: " + outputDir);

        Path artifactDir = ctx.getArtifactRoot().resolve(ctx.getProject().getCode()).resolve("tar");
        Path target = artifactDir.resolve(ctx.getProject().getCode() + "-" + ctx.getVersion() + ".tar.gz");
        TarUtils.tarGz(dist, target, ctx.getLog());
        ctx.getLog().line("[forge] 前端包完成: " + target.getFileName());

        Artifact artifact = new Artifact();
        artifact.setType(ArtifactType.TAR);
        artifact.setVersion(ctx.getVersion());
        artifact.setRef(ctx.getProject().getCode() + "/tar/" + target.getFileName());
        artifact.setChecksum(TarUtils.sha256(target));
        try { artifact.setSize(Files.size(target)); } catch (Exception ignore) {}
        artifact.setExtra(java.util.Map.of("outputDir", outputDir));
        ctx.payload("frontendDistRel", outputDir);
        return artifact;
    }

    private String detectPackageManager(Path src) {
        if (Files.exists(src.resolve("pnpm-lock.yaml"))) return "pnpm";
        if (Files.exists(src.resolve("yarn.lock"))) return "yarn";
        return "npm";
    }
}
