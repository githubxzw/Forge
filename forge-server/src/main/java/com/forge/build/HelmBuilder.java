package com.forge.build;

import com.forge.common.ApiException;
import com.forge.domain.Artifact;
import com.forge.domain.ArtifactType;
import com.forge.domain.BuildTarget;
import com.forge.domain.Project;
import com.forge.domain.ProjectType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** K8s 制品构建：项目自带 chart 优先，否则用内置通用 Chart，产出 helm tgz */
@Component
public class HelmBuilder implements Builder {

    @Override
    public BuildTarget target() { return BuildTarget.CHART; }

    @Override
    public Artifact build(BuildContext ctx) {
        String chartPath = ctx.config("chartPath");
        boolean builtin = chartPath == null || chartPath.isBlank();
        Path chartDir;
        List<String> sets = new ArrayList<>();

        // 镜像信息：优先取本次构建的镜像，否则按命名规则推算
        String imageName = ctx.payload("imageName") instanceof String n ? n : DockerBuilder.imageNameOf(ctx);
        String imageTag = ctx.payload("imageTag") instanceof String t ? t : ctx.getVersion();
        int containerPort = intConfig(ctx, "containerPort",
                ctx.getProject().getType() == ProjectType.FRONTEND ? 80 : 8080);
        int replicas = intConfig(ctx, "replicas", 1);

        if (builtin) {
            chartDir = ctx.getWorkDir().resolve("forge-chart");
            ClasspathTemplates.copyChart(chartDir, ctx.getProject().getCode());
            // 内置模板：镜像信息直接写入 values.yaml；项目配置了健康检查路径时同步启用 K8s 探针
            //（deployment.yaml 本就支持 readiness/liveness，此前硬编码 enabled: false 白白浪费）
            String hcPath = healthCheckPath(ctx.getProject());
            // Ingress：buildConfig.ingressHost 配置即启用；HPA：maxReplicas > replicas 即启用
            String ingressHost = ctx.config("ingressHost");
            int maxReplicas = intConfig(ctx, "maxReplicas", 0);
            String values = renderBuiltinValues(replicas, imageName, imageTag, containerPort,
                    hcPath != null, hcPath == null ? "/" : hcPath,
                    ingressHost, maxReplicas);
            try {
                Files.writeString(chartDir.resolve("values.yaml"), values);
            } catch (IOException e) {
                throw new ApiException(500, "写 values.yaml 失败: " + e.getMessage());
            }
        } else {
            chartDir = ctx.getSrcDir().resolve(chartPath);
            if (!Files.isDirectory(chartDir.resolve("templates")) || !Files.exists(chartDir.resolve("Chart.yaml"))) {
                throw new ApiException("chart 目录不合法（需要 Chart.yaml 与 templates/）: " + chartPath);
            }
            // 自带 chart：镜像信息通过 --set 注入
            sets.add("image.repository=" + imageName);
            sets.add("image.tag=" + imageTag);
            if (ctx.config("containerPort") != null) sets.add("containerPort=" + containerPort);
        }

        // helm package
        Path out = ctx.getOutDir().resolve("chart");
        try {
            Files.createDirectories(out);
        } catch (IOException e) {
            throw new ApiException(500, e.getMessage());
        }
        List<String> cmd = new ArrayList<>(List.of("helm", "package",
                chartDir.toAbsolutePath().toString(),
                "--version", ctx.getVersion(), "--app-version", ctx.getVersion(),
                "-d", out.toAbsolutePath().toString()));
        ctx.run(cmd, null, 600);

        Path tgz = findTgz(out).orElseThrow(() -> new ApiException("helm package 未产出 tgz"));
        Path artifactDir = ctx.getArtifactRoot().resolve(ctx.getProject().getCode()).resolve("chart");
        Path target = artifactDir.resolve(tgz.getFileName());
        try {
            Files.createDirectories(target.getParent());
            Files.move(tgz, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ApiException(500, "移动 chart 包失败: " + e.getMessage());
        }
        ctx.getLog().line("[forge] Chart 打包完成: " + target.getFileName());

        Artifact artifact = new Artifact();
        artifact.setType(ArtifactType.CHART);
        artifact.setVersion(ctx.getVersion());
        artifact.setRef(ctx.getProject().getCode() + "/chart/" + target.getFileName());
        artifact.setChecksum(TarUtils.sha256(target));
        try { artifact.setSize(Files.size(target)); } catch (Exception ignore) {}
        artifact.setExtra(Map.of(
                "imageName", imageName,
                "tag", imageTag,
                "sets", sets,
                "chartSource", builtin ? "builtin" : "custom"));
        return artifact;
    }

    /** 项目健康检查配置的 path（url 形式配置时取其路径部分）；未配置返回 null → 内置 chart 不启用探针 */
    static String healthCheckPath(Project project) {
        Map<String, Object> hc = project.getHealthCheck();
        if (hc == null) return null;
        Object path = hc.get("path");
        if (path instanceof String s && !s.isBlank()) return s;
        Object url = hc.get("url");
        if (url instanceof String u && !u.isBlank()) {
            try {
                var uri = java.net.URI.create(u.trim());
                return (uri.getPath() == null || uri.getPath().isBlank()) ? "/" : uri.getPath();
            } catch (Exception ignore) {}
        }
        return null;
    }

    static String renderBuiltinValues(int replicas, String image, String tag, int containerPort,
                                      boolean healthEnabled, String healthPath,
                                      String ingressHost, int maxReplicas) {
        boolean ingressEnabled = ingressHost != null && !ingressHost.isBlank();
        boolean hpaEnabled = maxReplicas > replicas;
        return """
                replicaCount: %d
                image:
                  repository: %s
                  tag: "%s"
                containerPort: %d
                env: {}
                healthCheck:
                  enabled: %b
                  path: %s
                ingress:
                  enabled: %b
                  host: "%s"
                  className: nginx
                autoscaling:
                  enabled: %b
                  minReplicas: %d
                  maxReplicas: %d
                  targetCPUUtilizationPercentage: 80
                resources:
                  requests:
                    cpu: 100m
                    memory: 256Mi
                  limits:
                    cpu: "1"
                    memory: 512Mi
                """.formatted(replicas, image, tag, containerPort, healthEnabled, healthPath,
                ingressEnabled, ingressEnabled ? ingressHost : "",
                hpaEnabled, replicas, Math.max(maxReplicas, replicas));
    }

    private int intConfig(BuildContext ctx, String key, int def) {
        Object v = ctx.getProject().getBuildConfig() == null ? null : ctx.getProject().getBuildConfig().get(key);
        if (v instanceof Number n) return n.intValue();
        if (v != null) {
            try { return Integer.parseInt(String.valueOf(v)); } catch (Exception ignore) {}
        }
        return def;
    }

    private java.util.Optional<Path> findTgz(Path dir) {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".tgz")).findFirst();
        } catch (IOException e) {
            return java.util.Optional.empty();
        }
    }
}
