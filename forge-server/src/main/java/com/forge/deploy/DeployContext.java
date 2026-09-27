package com.forge.deploy;

import com.forge.domain.Artifact;
import com.forge.domain.Environment;
import com.forge.domain.Project;
import com.forge.infra.log.LogSink;
import com.forge.setting.SettingService;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.nio.file.Path;
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
    /** 回滚目标（rollback 时非空）：提供 version/revision 信息 */
    @Setter
    private Integer rollbackRevision;

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
