package com.forge.deploy;

import com.forge.common.ApiException;
import com.forge.domain.ArtifactType;
import com.forge.domain.EnvKind;
import com.forge.infra.process.ProcessRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * K8s 部署（Helm）：helm upgrade --install + --wait；回滚用 helm rollback 到历史 revision。
 * 环境变量中 key 以 helm. 开头的项作为 --set 注入。
 */
@Component
@RequiredArgsConstructor
public class K8sDeployer implements Deployer {

    @Override
    public EnvKind kind() { return EnvKind.K8S; }

    @Override
    public Map<String, Object> deploy(DeployContext ctx) {
        if (ctx.getArtifact().getType() != ArtifactType.CHART) {
            throw new ApiException("K8S 环境需要 CHART 制品（请构建 K8s Chart 后部署）");
        }
        String namespace = ctx.conn("namespace");
        if (namespace == null || namespace.isBlank()) throw new ApiException("K8S 环境未配置 namespace");
        String release = ctx.getProject().getCode();
        String kubeconfig = ctx.getSshService().resolveKubeconfigFile(ctx.getEnvironment(), ctx.getLog());

        List<String> cmd;
        if (ctx.getRollbackRevision() != null) {
            ctx.getLog().line("[forge] helm rollback " + release + " → revision " + ctx.getRollbackRevision());
            cmd = new ArrayList<>(List.of("helm", "rollback", release, String.valueOf(ctx.getRollbackRevision()),
                    "-n", namespace, "--wait", "--timeout", "600s", "--kubeconfig", kubeconfig));
        } else {
            cmd = new ArrayList<>(List.of("helm", "upgrade", "--install", release,
                    ctx.getArtifactFile().toAbsolutePath().toString(),
                    "-n", namespace, "--create-namespace",
                    "--wait", "--timeout", "600s", "--kubeconfig", kubeconfig));
            for (String set : collectSets(ctx)) {
                cmd.add("--set");
                cmd.add(set);
            }
        }
        return runHelm(ctx, cmd, kubeconfig, release, namespace);
    }

    /** --set 列表：制品内嵌 sets（自带 chart 场景） + 环境变量 helm.* */
    List<String> collectSets(DeployContext ctx) {
        List<String> sets = new ArrayList<>();
        Object extraSets = ctx.extra().get("sets");
        if (extraSets instanceof List<?> l) l.forEach(s -> sets.add(String.valueOf(s)));
        ctx.envVars().forEach((k, v) -> {
            if (k.startsWith("helm.")) sets.add(k.substring(5) + "=" + v);
        });
        return sets;
    }

    private Map<String, Object> runHelm(DeployContext ctx, List<String> cmd, String kubeconfig,
                                        String release, String namespace) {
        com.forge.infra.log.LogSink log = ctx.getLog();
        try {
            log.line("[forge] 执行: " + String.join(" ", cmd));
            ctx.run(cmd, null, 900);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("release", release);
            result.put("namespace", namespace);
            try {
                String history = ProcessRunner.capture(List.of("helm", "history", release,
                        "-n", namespace, "--max", "1", "-o", "json", "--kubeconfig", kubeconfig), null, 60);
                var node = com.forge.common.JsonUtils.mapper().readTree(history);
                if (node.isArray() && !node.isEmpty()) {
                    result.put("revision", node.get(node.size() - 1).path("revision").asInt());
                }
            } catch (Exception ignore) {}
            log.line("[forge] Helm 部署完成" + (result.containsKey("revision") ? "，revision: " + result.get("revision") : ""));
            return result;
        } finally {
            try { Files.deleteIfExists(Path.of(kubeconfig)); } catch (Exception ignore) {}
        }
    }
}
