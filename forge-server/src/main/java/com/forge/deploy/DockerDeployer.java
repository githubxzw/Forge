package com.forge.deploy;

import com.forge.common.ApiException;
import com.forge.domain.ArtifactType;
import com.forge.domain.EnvKind;
import com.forge.domain.ProjectType;
import com.forge.infra.log.LogSink;
import com.forge.infra.ssh.SshExecutor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Docker 主机部署（compose）：渲染 docker-compose.yml → 上传 → docker compose up -d。
 * host 为空时使用构建机本机 docker；否则通过 SSH 在远端执行。
 */
@Component
public class DockerDeployer implements Deployer {

    @Override
    public EnvKind kind() { return EnvKind.DOCKER; }

    @Override
    public Map<String, Object> deploy(DeployContext ctx) {
        LogSink log = ctx.getLog();
        if (ctx.getArtifact().getType() != ArtifactType.IMAGE) {
            throw new ApiException("DOCKER 环境需要 IMAGE 制品（请构建镜像后再部署）");
        }
        String composeDir = ctx.conn("composeDir");
        if (composeDir == null || composeDir.isBlank()) throw new ApiException("DOCKER 环境未配置 composeDir");
        String code = ctx.getProject().getCode();
        String remoteDir = composeDir.replaceAll("/+$", "") + "/" + code;
        String composeFile = remoteDir + "/docker-compose.yml";
        String content = renderCompose(ctx);

        Map<String, Object> result = new LinkedHashMap<>();
        String host = ctx.conn("host");
        boolean remote = host != null && !host.isBlank();

        if (remote) {
            try (SshExecutor ssh = ctx.getSshService().connect(ctx.getEnvironment())) {
                // 登记取消句柄：断开会话可中止进行中的镜像传输/远端命令
                ctx.registerCancel(ssh::close);
                log.line("[forge] 连接 " + host + " 成功");
                ssh.uploadText(content, composeFile, log);
                ensureImageOnRemote(ctx, ssh);
                runRemote(ssh, composeFile, code, ctx);
            }
        } else {
            log.line("[forge] 本机 docker 部署");
            try {
                Path local = Path.of(composeDir, code, "docker-compose.yml");
                Files.createDirectories(local.getParent());
                Files.writeString(local, content, StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new ApiException(500, "写 compose 文件失败: " + e.getMessage());
            }
            runLocal(composeFile, code, ctx);
        }
        result.put("composeFile", composeFile);
        result.put("image", ctx.getArtifact().getRef());
        result.put("remote", remote);
        return result;
    }

    private void runRemote(SshExecutor ssh, String composeFile, String code, DeployContext ctx) {
        LogSink log = ctx.getLog();
        ssh.execOrThrow("docker compose -p " + code + " -f " + composeFile + " up -d", log, 1800);
        log.line("[forge] 容器已启动");
        ssh.exec("docker compose -p " + code + " ps", log, 60);
    }

    /**
     * 确保镜像在目标主机可用：
     * 1. 制品来自配置的 registry → 远端 docker pull（失败则降级）；
     * 2. 无 registry → 构建机 docker save + SFTP 传输 + 远端 docker load。
     */
    private void ensureImageOnRemote(DeployContext ctx, SshExecutor ssh) {
        LogSink log = ctx.getLog();
        String ref = ctx.getArtifact().getRef();
        String registry = ctx.getSettings().get("registry");
        boolean fromRegistry = registry != null && !registry.isBlank() && ref.startsWith(registry + "/");
        if (fromRegistry) {
            log.line("[forge] 在目标主机拉取镜像: " + ref);
            try {
                ssh.execOrThrow("docker pull " + ref, log, 1800);
                log.line("[forge] 镜像拉取成功");
                return;
            } catch (Exception e) {
                log.line("[forge] 远端拉取失败（" + e.getMessage() + "），降级为本地导出传输");
            }
        }
        log.line("[forge] 本机导出镜像 " + ref + "（未配置镜像仓库或拉取失败）");
        Path tmp;
        try {
            tmp = Files.createTempFile("forge-image-", ".tar.gz");
        } catch (Exception e) {
            throw new ApiException(500, "创建临时文件失败: " + e.getMessage());
        }
        try {
            // docker save 管道 gzip 压缩后落盘（经 ctx.run 登记取消句柄，取消部署可中断导出）
            ctx.run(List.of("sh", "-c", "docker save " + ref + " | gzip > " + tmp), null, 1800);
            long mb = Files.size(tmp) / 1048576;
            log.line("[forge] 上传镜像（约 " + mb + " MB）...");
            ssh.upload(tmp, "/tmp/forge-image.tar.gz", log);
            ssh.execOrThrow("docker load -i /tmp/forge-image.tar.gz && rm -f /tmp/forge-image.tar.gz", log, 1800);
            log.line("[forge] 镜像已在目标主机就绪");
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(500, "镜像传输失败: " + e.getMessage());
        } finally {
            try { Files.deleteIfExists(tmp); } catch (Exception ignore) {}
        }
    }

    private void runLocal(String composeFile, String code, DeployContext ctx) {
        ctx.run(List.of("docker", "compose", "-p", code, "-f", composeFile, "up", "-d"), null, 1800);
        ctx.getLog().line("[forge] 容器已启动");
        ctx.run(List.of("docker", "compose", "-p", code, "ps"), null, 60);
    }

    /** 渲染 compose 内容：镜像 + 端口 + 环境变量 */
    String renderCompose(DeployContext ctx) {
        Map<String, Object> bc = ctx.getProject().getBuildConfig();
        StringBuilder sb = new StringBuilder();
        sb.append("services:\n");
        sb.append("  ").append(ctx.getProject().getCode()).append(":\n");
        sb.append("    image: ").append(ctx.getArtifact().getRef()).append("\n");
        sb.append("    container_name: ").append(ctx.getProject().getCode()).append("\n");
        sb.append("    restart: unless-stopped\n");

        // 端口：buildConfig.ports 为 "host:container" 列表；缺省映射 containerPort
        Object ports = bc == null ? null : bc.get("ports");
        List<String> portList = new ArrayList<>();
        if (ports instanceof List<?> l) {
            l.forEach(p -> portList.add(String.valueOf(p)));
        } else if (ports != null && !String.valueOf(ports).isBlank()) {
            portList.add(String.valueOf(ports));
        }
        if (portList.isEmpty()) {
            int containerPort = 8080;
            Object cp = bc == null ? null : bc.get("containerPort");
            if (cp instanceof Number n) containerPort = n.intValue();
            portList.add(containerPort + ":" + containerPort);
        }
        sb.append("    ports:\n");
        for (String p : portList) sb.append("      - \"").append(p).append("\"\n");

        // 环境变量：env.vars 中非 helm. 前缀项
        Map<String, String> vars = new LinkedHashMap<>();
        ctx.envVars().forEach((k, v) -> {
            if (!k.startsWith("helm.") && !k.equals("java.opts") && !k.equals("server.port")) vars.put(k, v);
        });
        if (!vars.isEmpty()) {
            sb.append("    environment:\n");
            vars.forEach((k, v) -> sb.append("      ").append(k).append(": \"").append(v.replace("\"", "\\\"")).append("\"\n"));
        }
        return sb.toString();
    }
}
