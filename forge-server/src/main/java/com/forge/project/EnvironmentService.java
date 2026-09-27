package com.forge.project;

import com.forge.common.ApiException;
import com.forge.domain.Environment;
import com.forge.domain.EnvironmentRepository;
import com.forge.domain.EnvKind;
import com.forge.infra.log.LogSink;
import com.forge.infra.ssh.SshExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EnvironmentService {
    private final EnvironmentRepository repository;
    private final com.forge.domain.DeploymentRepository deploymentRepository;
    private final com.forge.infra.ssh.SshService sshService;

    public List<Environment> list() {
        return repository.findAll();
    }

    public Environment require(String id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("环境"));
    }

    public Environment create(Environment e) {
        if (e.getKind() == null) throw new ApiException("环境类型不能为空");
        return repository.save(e);
    }

    @Transactional
    public Environment update(String id, Environment in) {
        Environment e = require(id);
        if (in.getName() != null) e.setName(in.getName());
        if (in.getKind() != null) e.setKind(in.getKind());
        if (in.getConnConfig() != null) e.setConnConfig(in.getConnConfig());
        if (in.getVars() != null) e.setVars(in.getVars());
        return repository.save(e);
    }

    @Transactional
    public void delete(String id) {
        // 有部署历史的环境不允许删除（外键引用 + 审计完整性）
        if (deploymentRepository.existsByEnvironmentId(id)) {
            throw new ApiException("该环境存在部署历史，为保证审计完整性不允许删除");
        }
        repository.deleteById(id);
    }

    /** 连通性测试：SSH 直连执行 echo；DOCKER 测 docker 版本（本地或 SSH）；K8S 测 kubectl cluster-info */
    public Map<String, Object> test(String id) {
        Environment env = require(id);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        switch (env.getKind()) {
            case SSH -> {
                try (SshExecutor ssh = sshService.connect(env)) {
                    String out = ssh.capture("echo ok && uname -a", 30);
                    result.put("ok", out.contains("ok"));
                    result.put("detail", out);
                }
            }
            case DOCKER -> {
                String host = str(env.getConnConfig(), "host");
                if (host == null || host.isBlank()) {
                    String ver = captureLocal("docker", "--version");
                    result.put("ok", ver != null);
                    result.put("detail", ver == null ? "本机 docker 不可用" : ver);
                } else {
                    try (SshExecutor ssh = sshService.connect(env)) {
                        String out = ssh.capture("docker version --format '{{.Server.Version}}'", 60);
                        result.put("ok", !out.isBlank());
                        result.put("detail", "docker server: " + out);
                    }
                }
            }
            case K8S -> {
                String kubeconfig = sshService.resolveKubeconfigFile(env, null);
                try {
                    String out = com.forge.infra.process.ProcessRunner.capture(
                            List.of("kubectl", "--kubeconfig", kubeconfig, "cluster-info"), null, 60);
                    result.put("ok", out.contains("is running"));
                    result.put("detail", out);
                } finally {
                    try { java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(kubeconfig)); } catch (Exception ignore) {}
                }
            }
            default -> throw new ApiException("未知环境类型");
        }
        return result;
    }

    private String captureLocal(String tool, String arg) {
        try {
            return com.forge.infra.process.ProcessRunner.capture(List.of(tool, arg), null, 30);
        } catch (Exception e) {
            return null;
        }
    }

    public static String str(Map<String, Object> map, String key) {
        Object v = map == null ? null : map.get(key);
        return v == null ? null : String.valueOf(v);
    }

    public static int intVal(Map<String, Object> map, String key, int def) {
        Object v = map == null ? null : map.get(key);
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return def; }
    }
}
