package com.forge.build;

import com.forge.common.ApiException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** 从 classpath 拷贝内置模板（Dockerfile / Helm Chart），资源清单固定，兼容 jar 内运行 */
public final class ClasspathTemplates {
    public static final List<String> CHART_FILES = List.of(
            "templates/chart/Chart.yaml",
            "templates/chart/values.yaml",
            "templates/chart/templates/deployment.yaml",
            "templates/chart/templates/service.yaml");

    private ClasspathTemplates() {}

    public static void copyChart(Path targetDir, String appName) {
        try {
            for (String res : CHART_FILES) {
                Path out = targetDir.resolve(res.substring("templates/chart/".length()));
                Files.createDirectories(out.getParent());
                String content = read(res)
                        .replace("{{APP_NAME}}", sanitize(appName));
                Files.writeString(out, content);
            }
        } catch (IOException e) {
            throw new ApiException(500, "拷贝内置 Chart 失败: " + e.getMessage());
        }
    }

    public static String dockerfile(String name) {
        return read("templates/docker/" + name);
    }

    private static String read(String resource) {
        try (InputStream in = ClasspathTemplates.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new ApiException(500, "内置模板缺失: " + resource);
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ApiException(500, "读取内置模板失败: " + resource);
        }
    }

    public static String sanitize(String s) {
        return s == null ? "app" : s.toLowerCase().replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}
