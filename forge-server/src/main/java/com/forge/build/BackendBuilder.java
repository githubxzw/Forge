package com.forge.build;

import com.forge.common.ApiException;
import com.forge.domain.Artifact;
import com.forge.domain.ArtifactType;
import com.forge.domain.BuildTarget;
import com.forge.infra.process.ProcessRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** 后端包构建：Maven/Gradle → jar */
@Component
public class BackendBuilder implements Builder {

    @Override
    public BuildTarget target() { return BuildTarget.BACKEND_JAR; }

    @Override
    public Artifact build(BuildContext ctx) {
        Path src = ctx.getSrcDir();
        boolean maven = Files.exists(src.resolve("pom.xml"));
        boolean gradle = Files.exists(src.resolve("build.gradle")) || Files.exists(src.resolve("build.gradle.kts"));
        if (!maven && !gradle) throw new ApiException("未找到 pom.xml 或 build.gradle，无法构建后端包");

        List<String> cmd = new ArrayList<>();
        String override = ctx.config("buildCommand");
        if (override != null && !override.isBlank()) {
            cmd.addAll(List.of(override.trim().split("\\s+")));
            cmd = withAbsoluteWrapper(cmd, src);
        } else if (maven) {
            boolean hasWrapper = Files.exists(src.resolve("mvnw"));
            if (hasWrapper) {
                java.io.File mvnw = src.resolve("mvnw").toFile();
                if (!mvnw.canExecute()) mvnw.setExecutable(true);
                cmd.add("./mvnw");
            } else {
                cmd.add("mvn");
            }
            cmd.addAll(List.of("-B", "-ntp", "clean", "package", "-DskipTests"));
            // Maven 镜像加速：生成临时 settings.xml
            String mirror = ctx.setting("mavenMirror");
            if (mirror != null && !mirror.isBlank()) {
                Path settings = ctx.getWorkDir().resolve("maven-settings.xml");
                writeMavenSettings(settings, mirror);
                cmd.add("-s");
                cmd.add(settings.toAbsolutePath().toString());
            }
        } else {
            boolean hasWrapper = Files.exists(src.resolve("gradlew"));
            if (hasWrapper) {
                java.io.File gw = src.resolve("gradlew").toFile();
                if (!gw.canExecute()) gw.setExecutable(true);
                cmd.add("./gradlew");
            } else {
                cmd.add("gradle");
            }
            cmd.addAll(List.of("bootJar", "--no-daemon", "-x", "test"));
        }
        ctx.getLog().line("[forge] 后端构建开始: " + String.join(" ", cmd));
        ProcessRunner.run(cmd, src, ctx.getEnv(), ctx.getLog(), null, 3600);

        // 定位 jar（排除 original/sources/javadoc）
        Path jar = findJar(src).orElseThrow(() -> new ApiException("构建完成但未找到 jar 产物"));

        Path artifactDir = ctx.getArtifactRoot().resolve(ctx.getProject().getCode()).resolve("jar");
        Path target = artifactDir.resolve(ctx.getProject().getCode() + "-" + ctx.getVersion() + ".jar");
        try {
            Files.createDirectories(target.getParent());
            Files.copy(jar, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ApiException(500, "复制 jar 失败: " + e.getMessage());
        }
        ctx.getLog().line("[forge] 后端包完成: " + target.getFileName());

        Artifact artifact = new Artifact();
        artifact.setType(ArtifactType.JAR);
        artifact.setVersion(ctx.getVersion());
        artifact.setRef(ctx.getProject().getCode() + "/jar/" + target.getFileName());
        artifact.setChecksum(TarUtils.sha256(target));
        try { artifact.setSize(Files.size(target)); } catch (Exception ignore) {}
        artifact.setExtra(java.util.Map.of("source", jar.toString()));
        ctx.payload("backendJarRel", src.relativize(jar).toString().replace('\\', '/'));
        return artifact;
    }

    /** buildCommand 覆盖时，若首个命令是 mvnw/gradlew 则替换为可执行路径写法 */
    private List<String> withAbsoluteWrapper(List<String> cmd, Path src) {
        List<String> result = new ArrayList<>();
        for (String s : cmd) {
            if (s.equals("mvnw") || s.equals("./mvnw") || s.equals("gradlew") || s.equals("./gradlew")) {
                java.io.File f = src.resolve(s.replace("./", "")).toFile();
                if (f.exists()) {
                    if (!f.canExecute()) f.setExecutable(true);
                    result.add("./" + s.replace("./", ""));
                    continue;
                }
            }
            result.add(s);
        }
        return result;
    }

    private Optional<Path> findJar(Path src) {
        Path target = src.resolve("target");
        Path libs = src.resolve("build").resolve("libs");
        try (Stream<Path> s1 = Files.exists(target) ? Files.walk(target) : java.util.stream.Stream.empty();
             Stream<Path> s2 = Files.exists(libs) ? Files.walk(libs) : java.util.stream.Stream.empty()) {
            return java.util.stream.Stream.concat(s1, s2)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return !name.endsWith("-original.jar") && !name.contains("sources")
                                && !name.contains("javadoc") && !name.contains("tests");
                    })
                    .max(java.util.Comparator.comparingLong(p -> p.toFile().lastModified()));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private void writeMavenSettings(Path file, String mirrorUrl) {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
                  <mirrors>
                    <mirror>
                      <id>forge-mirror</id>
                      <mirrorOf>central</mirrorOf>
                      <url>%s</url>
                    </mirror>
                  </mirrors>
                </settings>
                """.formatted(mirrorUrl);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, xml, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ApiException(500, "生成 Maven settings 失败: " + e.getMessage());
        }
    }
}
