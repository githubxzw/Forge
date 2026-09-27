package com.forge.infra.git;

import com.forge.domain.CredentialKind;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.net.URI;

/**
 * Git 拉取凭据：
 * - SSH_KEY：写临时私钥，通过 GIT_SSH_COMMAND 指定（ssh 地址）
 * - PASSWORD / TEXT：token/密码写入临时 credential-store 文件（https 地址），secret 不出现在命令行
 * try-with-resources 使用，close 清理临时文件。
 */
public class GitAuth implements AutoCloseable {
    public static final GitAuth NONE = new GitAuth(List.of(), Map.of(), List.of());

    private final List<String> credArgs;
    private final Map<String, String> env;
    private final List<Path> tempFiles;

    private GitAuth(List<String> credArgs, Map<String, String> env, List<Path> tempFiles) {
        this.credArgs = credArgs;
        this.env = env;
        this.tempFiles = tempFiles;
    }

    /** 额外的 git -c 参数（插入到子命令之前） */
    public List<String> args() { return credArgs; }

    /** 附加环境变量（GIT_SSH_COMMAND 等） */
    public Map<String, String> env() { return env; }

    public static GitAuth of(CredentialKind kind, Map<String, String> data, String repoUrl) {
        if (kind == null || data == null || data.isEmpty()) return NONE;
        try {
            if (kind == CredentialKind.SSH_KEY) {
                String key = data.get("privateKey");
                if (key == null || key.isBlank()) return NONE;
                Path keyFile = Files.createTempFile("forge-git-", ".key");
                Files.writeString(keyFile, key);
                Files.setPosixFilePermissions(keyFile, PosixFilePermissions.fromString("rw-------"));
                String ssh = "ssh -i " + keyFile.toAbsolutePath()
                        + " -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o IdentitiesOnly=yes";
                return new GitAuth(List.of(), Map.of("GIT_SSH_COMMAND", ssh), List.of(keyFile));
            }

            String host = hostOf(repoUrl);
            if (host == null) return NONE;
            String user;
            String secret;
            if (kind == CredentialKind.PASSWORD) {
                user = data.getOrDefault("username", "forge");
                secret = data.get("password");
            } else {
                user = "x-access-token";
                secret = data.get("token") != null && !data.get("token").isBlank() ? data.get("token") : data.get("value");
            }
            if (secret == null || secret.isBlank()) return NONE;

            Path credFile = Files.createTempFile("forge-git-cred-", ".txt");
            Files.writeString(credFile, "https://" + user + ":" + secret + "@" + host + "\n");
            Files.setPosixFilePermissions(credFile, PosixFilePermissions.fromString("rw-------"));
            List<String> args = List.of("-c", "credential.helper=store --file=" + credFile.toAbsolutePath());
            return new GitAuth(args, Map.of(), List.of(credFile));
        } catch (IOException e) {
            throw new IllegalStateException("初始化 Git 凭据失败: " + e.getMessage(), e);
        }
    }

    private static String hostOf(String repoUrl) {
        try {
            URI uri = new URI(repoUrl);
            String host = uri.getHost();
            return host == null ? null : host + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void close() {
        for (Path p : tempFiles) {
            try { Files.deleteIfExists(p); } catch (IOException ignore) {}
        }
    }
}
