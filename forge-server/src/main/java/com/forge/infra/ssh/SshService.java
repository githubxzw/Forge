package com.forge.infra.ssh;

import com.forge.credential.CredentialService;
import com.forge.domain.Credential;
import com.forge.domain.CredentialKind;
import com.forge.domain.Environment;
import com.forge.project.EnvironmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** 根据环境配置构建 SSH 执行器 / 解析 kubeconfig 临时文件 */
@Component
@RequiredArgsConstructor
public class SshService {
    private final CredentialService credentialService;

    public SshExecutor connect(Environment env) {
        Map<String, Object> conn = env.getConnConfig();
        String host = EnvironmentService.str(conn, "host");
        int port = EnvironmentService.intVal(conn, "port", 22);
        String username = EnvironmentService.str(conn, "username");
        String credentialId = EnvironmentService.str(conn, "credentialId");
        if (host == null || host.isBlank()) throw new com.forge.common.ApiException("环境未配置主机地址");
        if (username == null || username.isBlank()) throw new com.forge.common.ApiException("环境未配置用户名");

        String password = null, privateKey = null, passphrase = null;
        if (credentialId != null && !credentialId.isBlank()) {
            Credential cred = credentialService.require(credentialId);
            Map<String, String> data = credentialService.data(cred);
            if (cred.getKind() == CredentialKind.SSH_KEY) {
                privateKey = data.get("privateKey");
                passphrase = data.get("passphrase");
            } else if (cred.getKind() == CredentialKind.PASSWORD) {
                password = data.get("password");
            }
        }
        return SshExecutor.connect(host, port, username, password, privateKey, passphrase);
    }

    /** 将环境引用的 KUBECONFIG 凭据写到临时文件，返回路径；调用方负责删除 */
    public String resolveKubeconfigFile(Environment env, com.forge.infra.log.LogSink sink) {
        Map<String, Object> conn = env.getConnConfig();
        String credId = EnvironmentService.str(conn, "kubeconfigCredentialId");
        if (credId == null || credId.isBlank()) throw new com.forge.common.ApiException("K8S 环境未配置 kubeconfig 凭据");
        Map<String, String> data = credentialService.dataById(credId);
        String content = data.get("kubeconfig");
        if (content == null || content.isBlank()) throw new com.forge.common.ApiException("kubeconfig 凭据内容为空");
        try {
            Path tmp = Files.createTempFile("forge-kubeconfig-", ".yaml");
            Files.writeString(tmp, content);
            try {
                // 集群凭据，仅本进程用户可读
                Files.setPosixFilePermissions(tmp,
                        java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignore) {}
            if (sink != null) sink.line("[forge] kubeconfig -> " + tmp);
            return tmp.toString();
        } catch (Exception e) {
            throw new com.forge.common.ApiException(500, "写 kubeconfig 临时文件失败: " + e.getMessage());
        }
    }
}
