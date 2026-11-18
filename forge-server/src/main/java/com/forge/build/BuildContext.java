package com.forge.build;

import com.forge.domain.Artifact;
import com.forge.domain.BuildTask;
import com.forge.domain.Project;
import com.forge.infra.log.LogSink;
import com.forge.infra.process.ProcessRunner;
import com.forge.setting.SettingService;
import lombok.Getter;

import java.io.Closeable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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
    /** 共享构建缓存根目录（{dataDir}/cache，跨构建复用依赖缓存，工作目录清理不影响） */
    private final Path cacheRoot;
    /** 取消句柄登记（BuildService 注入）：登记的进程/会话在取消构建时被关闭或整树击杀 */
    private final Consumer<Closeable> onCancel;
    private final Map<String, Object> payloads = new HashMap<>();
    private final Map<String, String> env = new HashMap<>();

    public BuildContext(Project project, BuildTask task, Path workDir, String branch,
                        String commit, String version, LogSink log, SettingService settings, Path artifactRoot,
                        Path cacheRoot, Consumer<Closeable> onCancel) {
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
        this.cacheRoot = cacheRoot;
        this.onCancel = onCancel;
        env.put("FORGE_VERSION", version);
        env.put("FORGE_BRANCH", branch);
        env.put("FORGE_COMMIT", commit);
        env.put("FORGE_PROJECT", project.getCode());
    }

    /**
     * 构建子进程统一入口：继承 FORGE_* 环境变量、输出进日志流，并把进程登记为取消句柄——
     * 此前各 Builder 直调 ProcessRunner 不登记，取消构建杀不掉进行中的 npm/mvn/docker/helm。
     */
    public int run(List<String> cmd, Path workDir, long timeoutSec) {
        return ProcessRunner.run(cmd, workDir, env, log, this::registerKill, timeoutSec);
    }

    /** 登记可关闭资源（如 SSH 会话）为取消句柄 */
    public void registerCancel(Closeable handle) {
        if (onCancel != null) onCancel.accept(handle);
    }

    /** 共享缓存子目录（如 npm/maven/gradle），自动创建；二次构建复用依赖，提速数倍 */
    public Path cacheDir(String name) {
        Path dir = cacheRoot.resolve(name);
        try {
            Files.createDirectories(dir);
        } catch (java.io.IOException e) {
            throw new com.forge.common.ApiException(500, "创建缓存目录失败: " + dir);
        }
        return dir;
    }

    private void registerKill(Process p) {
        registerCancel(() -> ProcessRunner.killTree(p));
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
