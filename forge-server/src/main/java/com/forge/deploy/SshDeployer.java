package com.forge.deploy;

import com.forge.common.ApiException;
import com.forge.domain.ArtifactType;
import com.forge.domain.EnvKind;
import com.forge.infra.log.LogSink;
import com.forge.infra.ssh.SshExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SSH 裸机部署：releases/{version} 目录 + current 软链原子切换。
 * - TAR（前端静态包）：解压到 release 目录，软链 current；nginx 根目录指向 {deployDir}/current
 * - JAR（后端包）：上传 app.jar + 生成 start.sh，软链后重启（systemd 或 pkill+nohup）
 */
@Component
@RequiredArgsConstructor
public class SshDeployer implements Deployer {

    @Override
    public EnvKind kind() { return EnvKind.SSH; }

    @Override
    public Map<String, Object> deploy(DeployContext ctx) {
        LogSink log = ctx.getLog();
        ArtifactType type = ctx.getArtifact().getType();
        if (type != ArtifactType.TAR && type != ArtifactType.JAR) {
            throw new ApiException("SSH 环境仅支持 TAR/JAR 制品（镜像请使用 DOCKER 环境，chart 请使用 K8S 环境）");
        }
        String deployDir = ctx.conn("deployDir");
        if (deployDir == null || deployDir.isBlank()) throw new ApiException("SSH 环境未配置 deployDir");
        String releases = deployDir + "/releases";
        String versionDir = releases + "/" + ctx.version();
        int keep = ctx.connInt("keepReleases", 5);
        String systemd = ctx.conn("systemdService");

        Map<String, Object> result = new LinkedHashMap<>();
        try (SshExecutor ssh = ctx.getSshService().connect(ctx.getEnvironment())) {
            log.line("[forge] 连接 " + ctx.conn("host") + " 成功");
            ssh.execOrThrow("mkdir -p " + releases + " " + deployDir + "/logs", log, 60);

            if (type == ArtifactType.TAR) {
                log.line("[forge] 上传前端包...");
                ssh.upload(ctx.getArtifactFile(), versionDir + "/pkg.tar.gz", log);
                log.line("[forge] 解压产物...");
                ssh.execOrThrow("tar -xzf " + versionDir + "/pkg.tar.gz -C " + versionDir
                        + " && rm -f " + versionDir + "/pkg.tar.gz", log, 300);
            } else {
                log.line("[forge] 上传 jar...");
                ssh.upload(ctx.getArtifactFile(), versionDir + "/app.jar", log);
                String javaOpts = ctx.envVars().getOrDefault("java.opts", "");
                String port = ctx.envVars().getOrDefault("server.port", "");
                // SSH 非交互 shell 的 PATH 常缺少 java，优先使用环境配置的 javaHome
                String javaHome = ctx.conn("javaHome");
                String javaBin = javaHome == null || javaHome.isBlank()
                        ? "java" : javaHome.replaceAll("/+$", "") + "/bin/java";
                String startScript = "#!/bin/sh\n"
                        + "cd " + deployDir + "/current\n"
                        + "exec " + javaBin + " " + javaOpts + " -jar app.jar"
                        + (port.isBlank() ? "" : " --server.port=" + port) + "\n";
                ssh.uploadText(startScript, versionDir + "/start.sh", log);
                ssh.execOrThrow("chmod +x " + versionDir + "/start.sh", log, 30);
            }

            // 清理旧 release（保留最近 keep 个，按修改时间；排除 current 软链指向的目录，防回滚后误删在用版本）
            ssh.execOrThrow(cleanupCommand(releases, deployDir + "/current", keep), log, 60);

            // 停止 → 软链 → 启动
            restart(ssh, ctx, deployDir, systemd, "stop");
            log.line("[forge] 切换软链 current -> releases/" + ctx.version());
            ssh.execOrThrow("ln -sfn " + versionDir + " " + deployDir + "/current", log, 30);
            restart(ssh, ctx, deployDir, systemd, "start");

            result.put("deployDir", deployDir);
            result.put("version", ctx.version());
            result.put("mode", type == ArtifactType.TAR ? "static" : "jar");
            return result;
        }
    }

    /**
     * 生成受保护的旧 release 清理命令：保留最近 keep 个，同时把 current 软链指向的目录
     * 从删除名单排除（回滚后 current 可能指向较旧的版本，裸按时间清理会删掉正在服务的版本）。
     */
    static String cleanupCommand(String releases, String currentLink, int keep) {
        return "CUR=$(readlink " + currentLink + " 2>/dev/null || true); "
                + "cd " + releases + " && ls -1t | tail -n +" + (keep + 1)
                + " | if [ -n \"$CUR\" ]; then grep -v -F \"$(basename \"$CUR\")\"; else cat; fi"
                + " | xargs -r rm -rf";
    }

    private void restart(SshExecutor ssh, DeployContext ctx, String deployDir, String systemd, String action) {
        LogSink log = ctx.getLog();
        if (systemd != null && !systemd.isBlank()) {
            log.line("[forge] systemctl " + action + " " + systemd);
            ssh.execOrThrow("sudo systemctl " + action + " " + systemd, log, 120);
            return;
        }
        if (ctx.getArtifact().getType() != ArtifactType.JAR) return; // 静态包无需重启
        String pattern = deployDir + "/current/app.jar";
        if ("stop".equals(action)) {
            log.line("[forge] 停止旧进程");
            ssh.exec("pkill -f '" + pattern + "' || true", log, 60);
        } else {
            log.line("[forge] 启动新进程");
            ssh.execOrThrow("cd " + deployDir
                    + " && nohup ./current/start.sh > " + deployDir + "/logs/" + ctx.version() + ".log 2>&1 < /dev/null &",
                    log, 60);
        }
    }
}
