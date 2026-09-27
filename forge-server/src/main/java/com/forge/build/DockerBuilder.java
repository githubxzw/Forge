package com.forge.build;

import com.forge.common.ApiException;
import com.forge.domain.Artifact;
import com.forge.domain.ArtifactType;
import com.forge.domain.BuildTarget;
import com.forge.domain.ProjectType;
import com.forge.infra.process.ProcessRunner;
import com.forge.setting.SettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Docker 镜像构建：项目自带 Dockerfile 优先，否则按构建产物使用内置模板 */
@Component
@RequiredArgsConstructor
public class DockerBuilder implements Builder {
    private final SettingService settings;

    @Override
    public BuildTarget target() { return BuildTarget.IMAGE; }

    @Override
    public Artifact build(BuildContext ctx) {
        Path src = ctx.getSrcDir();
        String imageName = imageNameOf(ctx);
        String ref = imageName + ":" + ctx.getVersion();

        // 1. 确定 Dockerfile：buildConfig.dockerfilePath > 仓库根 Dockerfile > 内置模板
        Path dockerfile;
        String dfPath = ctx.config("dockerfilePath");
        if (dfPath != null && !dfPath.isBlank()) {
            dockerfile = src.resolve(dfPath);
            if (!Files.exists(dockerfile)) throw new ApiException("Dockerfile 不存在: " + dfPath);
        } else if (Files.exists(src.resolve("Dockerfile"))) {
            dockerfile = src.resolve("Dockerfile");
        } else {
            dockerfile = generateFromTemplate(ctx);
        }
        ctx.getLog().line("[forge] 使用 Dockerfile: " + dockerfile);

        // 2. build
        List<String> cmd = new ArrayList<>(List.of("docker", "build", "-t", ref, "-f", dockerfile.toAbsolutePath().toString()));
        Object buildArgs = ctx.getProject().getBuildConfig() == null ? null : ctx.getProject().getBuildConfig().get("buildArgs");
        if (buildArgs instanceof Map<?, ?> args) {
            args.forEach((k, v) -> cmd.add("--build-arg=" + k + "=" + v));
        }
        cmd.add(".");
        ProcessRunner.run(cmd, src, ctx.getEnv(), ctx.getLog(), null, 3600);

        // 3. 可选自动推送
        if ("true".equalsIgnoreCase(ctx.setting("dockerAutoPush")) && settings.registryConfigured()) {
            dockerLogin(ctx);
            ProcessRunner.run(List.of("docker", "push", ref), null, ctx.getEnv(), ctx.getLog(), null, 1800);
        }

        ctx.getLog().line("[forge] 镜像构建完成: " + ref);
        Artifact artifact = new Artifact();
        artifact.setType(ArtifactType.IMAGE);
        artifact.setVersion(ctx.getVersion());
        artifact.setRef(ref);
        artifact.setExtra(Map.of("imageName", imageName, "tag", ctx.getVersion()));
        ctx.payload("imageName", imageName);
        ctx.payload("imageTag", ctx.getVersion());
        return artifact;
    }

    /** 项目无 Dockerfile 时按已有构建产物生成：jar → JRE 运行镜像；dist → nginx 静态镜像 */
    private Path generateFromTemplate(BuildContext ctx) {
        String content;
        if (ctx.payload("backendJarRel") instanceof String jarRel) {
            content = ClasspathTemplates.dockerfile("Dockerfile.backend")
                    .replace("{{JRE_BASE}}", ctx.setting("dockerBaseJre"))
                    .replace("{{JAR_PATH}}", jarRel);
        } else if (ctx.payload("frontendDistRel") instanceof String distRel) {
            content = ClasspathTemplates.dockerfile("Dockerfile.frontend")
                    .replace("{{NGINX_BASE}}", ctx.setting("dockerBaseNginx"))
                    .replace("{{OUTPUT_DIR}}", distRel);
        } else {
            throw new ApiException("项目无 Dockerfile，且本次构建没有可打包的 jar/dist 产物；请在项目配置中指定 dockerfilePath");
        }
        Path target = ctx.getWorkDir().resolve("Dockerfile.forge");
        try {
            Files.writeString(target, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ApiException(500, "生成 Dockerfile 失败: " + e.getMessage());
        }
        return target;
    }

    public static String imageNameOf(BuildContext ctx) {
        String override = ctx.config("imageName");
        String base = override != null && !override.isBlank() ? override : ctx.getProject().getCode();
        String registry = ctx.setting("registry");
        String name = registry != null && !registry.isBlank() ? registry + "/" + base : base;
        return name.toLowerCase().replaceAll("[^a-z0-9._/-]", "-");
    }

    void dockerLogin(BuildContext ctx) {
        String registry = ctx.setting("registry");
        String user = ctx.setting("registryUsername");
        String password = settings.registryPasswordPlain();
        if (user.isBlank() || password.isBlank()) {
            ctx.getLog().line("[forge] 未配置 registry 账号，跳过 docker login");
            return;
        }
        List<String> cmd = new ArrayList<>(List.of("docker", "login", "-u", user, "--password-stdin"));
        if (!registry.isBlank()) cmd.add(registry);
        ProcessRunner.runWithStdin(cmd, password, 120);
        ctx.getLog().line("[forge] docker login 成功: " + registry);
    }
}
