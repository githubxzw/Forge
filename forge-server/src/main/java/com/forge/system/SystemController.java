package com.forge.system;

import com.forge.common.ApiResult;
import com.forge.config.ForgeProperties;
import com.forge.infra.log.LogSink;
import com.forge.infra.process.ProcessRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** 系统信息：数据目录、构建机工具链探测 */
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {
    private final ForgeProperties props;

    @GetMapping("/info")
    public ApiResult<Map<String, Object>> info() throws IOException {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("dataDir", props.getDataDir());
        info.put("artifactDir", props.getArtifactDir());
        info.put("os", System.getProperty("os.name") + " / " + System.getProperty("os.arch"));

        Map<String, Object> tools = new LinkedHashMap<>();
        for (String tool : List.of("git", "docker", "helm", "kubectl", "node", "npm", "pnpm", "yarn", "mvn", "gradle", "tar")) {
            tools.put(tool, detect(tool));
        }
        info.put("tools", tools);
        return ApiResult.ok(info);
    }

    private Map<String, Object> detect(String tool) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            boolean exists;
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                exists = new java.io.File(tool + ".exe").exists() || new java.io.File(tool + ".cmd").exists();
            } else {
                exists = ProcessRunner.run(List.of("which", tool), null, null, LogSink.NOOP, null, 5) == 0;
            }
            if (!exists) {
                result.put("available", false);
                return result;
            }
            String[] versionCmd = switch (tool) {
                case "git" -> new String[]{"git", "--version"};
                case "docker" -> new String[]{"docker", "--version"};
                case "helm" -> new String[]{"helm", "version", "--short"};
                case "kubectl" -> new String[]{"kubectl", "version", "--client", "--output", "yaml"};
                case "node" -> new String[]{"node", "--version"};
                case "npm" -> new String[]{"npm", "--version"};
                case "mvn" -> new String[]{"mvn", "--version"};
                case "gradle" -> new String[]{"gradle", "--version"};
                case "tar" -> new String[]{"tar", "--version"};
                default -> new String[]{tool, "--version"};
            };
            String version = "";
            try {
                Process p = new ProcessBuilder(versionCmd).start();
                boolean ok = p.waitFor(5, TimeUnit.SECONDS);
                if (ok && p.exitValue() == 0) {
                    version = new String(p.getInputStream().readAllBytes()).lines().findFirst().orElse("");
                }
            } catch (Exception ignore) {}
            result.put("available", true);
            result.put("version", version);
        } catch (Exception e) {
            result.put("available", false);
        }
        return result;
    }
}
