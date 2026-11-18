package com.forge.deploy;

import com.forge.domain.Artifact;
import com.forge.domain.Environment;
import com.forge.domain.Project;
import com.forge.infra.log.LogSink;
import com.forge.setting.SettingService;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Getter
@Builder
public class DeployContext {
    private final Project project;
    private final Environment environment;
    private final Artifact artifact;
    private final LogSink log;
    private final SettingService settings;
    private final com.forge.infra.ssh.SshService sshService;
    private final Path artifactFile;      // 文件制品路径；IMAGE 为 null
    /** 取消句柄登记（DeployService 注入）：登记的进程/SSH 会话在取消部署时被关闭或整树击杀 */
    private final Consumer<Closeable> onCancel;
    /** 回滚目标（rollback 时非空）：提供 version/revision 信息 */
    @Setter
    private Integer rollbackRevision;

    /**
     * 部署子进程统一入口：输出进日志流，并把进程登记为取消句柄——
     * 此前 Deployer 直调 ProcessRunner 不登记，取消部署对 helm/compose 等长命令无效。
     */
    public int run(List<String> cmd, Path workDir, long timeoutSec) {
        return com.forge.infra.process.ProcessRunner.run(cmd, workDir, null, log, this::registerKill, timeoutSec);
    }

    /** 登记可关闭资源（如 SSH 会话）为取消句柄 */
    public void registerCancel(Closeable handle) {
        if (onCancel != null) onCancel.accept(handle);
    }

    private void registerKill(Process p) {
        registerCancel(() -> com.forge.infra.process.ProcessRunner.killTree(p));
    }

    public String version() { return artifact.getVersion(); }

    public Map<String, Object> extra() { return artifact.getExtra() == null ? Map.of() : artifact.getExtra(); }

    public Map<String, String> envVars() {
        return environment.getVars() == null ? Map.of() : environment.getVars();
    }

    public String conn(String key) {
        Object v = environment.getConnConfig() == null ? null : environment.getConnConfig().get(key);
        return v == null ? null : String.valueOf(v);
    }

    public int connInt(String key, int def) {
        Object v = environment.getConnConfig() == null ? null : environment.getConnConfig().get(key);
        if (v instanceof Number n) return n.intValue();
        if (v != null) { try { return Integer.parseInt(String.valueOf(v)); } catch (Exception ignore) {} }
        return def;
    }
}
