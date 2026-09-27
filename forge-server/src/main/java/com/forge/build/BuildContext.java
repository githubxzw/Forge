package com.forge.build;

import com.forge.domain.Artifact;
import com.forge.domain.BuildTask;
import com.forge.domain.Project;
import com.forge.infra.log.LogSink;
import com.forge.setting.SettingService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 构建上下文：贯穿一次构建任务的所有输入/输出。
 * payloads 用于构建目标之间的传递（如 Docker 构建消费前端 dist / 后端 jar 的相对路径）。
 */
@Getter
public class BuildContext {
    private final Project project;
    private final BuildTask task;
    private final Path workDir;
    private final Path srcDir;
    private final Path outDir;
    private final String branch;
    private final String commit;
    private final String version;
    private final LogSink log;
    private final SettingService settings;
    /** 全局制品目录（持久保存，工作目录构建成功后会被清理） */
    private final Path artifactRoot;
    private final Map<String, Object> payloads = new HashMap<>();
    private final Map<String, String> env = new HashMap<>();

    public BuildContext(Project project, BuildTask task, Path workDir, String branch,
                        String commit, String version, LogSink log, SettingService settings, Path artifactRoot) {
        this.project = project;
        this.task = task;
        this.workDir = workDir;
        this.srcDir = workDir.resolve("src");
        this.outDir = workDir.resolve("out");
        this.branch = branch;
        this.commit = commit;
        this.version = version;
        this.log = log;
        this.settings = settings;
        this.artifactRoot = artifactRoot;
        env.put("FORGE_VERSION", version);
        env.put("FORGE_BRANCH", branch);
        env.put("FORGE_COMMIT", commit);
        env.put("FORGE_PROJECT", project.getCode());
    }

    public String setting(String key) {
        return settings == null ? "" : settings.get(key);
    }

    /** 构建配置里的字符串项 */
    public String config(String key) {
        Object v = project.getBuildConfig() == null ? null : project.getBuildConfig().get(key);
        return v == null ? null : String.valueOf(v);
    }

    public void payload(String key, Object value) {
        payloads.put(key, value);
    }

    public Object payload(String key) {
        return payloads.get(key);
    }

    /** 由 BuildService 调用保存制品 */
    public interface ArtifactSaver {
        Artifact save(Artifact artifact);
    }
}
