package com.forge.infra.ssh;

import com.forge.common.ApiException;
import com.forge.infra.log.LogSink;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.sftp.client.SftpClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

/**
 * SSH 执行器（MINA sshd）：exec 命令逐行输出到日志、SFTP 上传文件。
 * try-with-resources 使用；close 时同时断开 session 与 client。
 */
@Slf4j
public class SshExecutor implements AutoCloseable {
    private static final long CONNECT_TIMEOUT_MS = 15_000;

    private final SshClient client;
    private final ClientSession session;

    private SshExecutor(SshClient client, ClientSession session) {
        this.client = client;
        this.session = session;
    }

    public static SshExecutor connect(String host, int port, String username,
                                      String password, String privateKey, String passphrase) {
        try {
            SshClient client = SshClient.setUpDefaultClient();
            client.start();
            ClientSession session = client.connect(username, host, port)
                    .verify(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS).getSession();
            boolean authed = false;
            if (privateKey != null && !privateKey.isBlank()) {
                Iterable<KeyPair> pairs = SecurityUtils.getKeyPairResourceParser()
                        .loadKeyPairs(session, NamedResource.ofName("forge-key"),
                                FilePasswordProvider.of(passphrase == null ? "" : passphrase),
                                new ByteArrayInputStream(privateKey.getBytes(StandardCharsets.UTF_8)));
                for (KeyPair kp : pairs) {
                    session.addPublicKeyIdentity(kp);
                    authed = true;
                }
            }
            if (password != null && !password.isBlank()) {
                session.addPasswordIdentity(password);
                authed = true;
            }
            if (!authed) {
                session.close();
                client.stop();
                throw new ApiException(400, "SSH 凭据缺失：需要密码或私钥");
            }
            session.auth().verify(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            return new SshExecutor(client, session);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.debug("SSH 连接失败", e);
            throw new ApiException(500, "SSH 连接失败: " + rootMessage(e));
        }
    }

    /** 执行命令，输出逐行写入 sink；返回 exit code */
    public int exec(String cmd, LogSink sink, long timeoutSec) {
        try (ClientChannel channel = session.createExecChannel(cmd)) {
            channel.setOut(lineOut(sink));
            channel.setErr(lineOut(sink));
            channel.open().verify(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            var events = channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), timeoutSec * 1000L);
            if (events.contains(ClientChannelEvent.TIMEOUT)) {
                throw new ApiException(500, "SSH 命令超时: " + cmd);
            }
            Integer code = channel.getExitStatus();
            return code == null ? -1 : code;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(500, "SSH 命令失败: " + cmd + " - " + rootMessage(e));
        }
    }

    /** 执行命令，失败即抛出 */
    public void execOrThrow(String cmd, LogSink sink, long timeoutSec) {
        int code = exec(cmd, sink, timeoutSec);
        if (code != 0) {
            throw new ApiException(500, "远程命令失败（exit " + code + "）: " + cmd);
        }
    }

    /** 执行并捕获输出（小命令） */
    public String capture(String cmd, long timeoutSec) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        LogSink collector = line -> {
            if (bos.size() < 8000) bos.writeBytes((line + "\n").getBytes(StandardCharsets.UTF_8));
        };
        exec(cmd, collector, timeoutSec);
        return bos.toString(StandardCharsets.UTF_8).trim();
    }

    /** 上传本地文件到远程（自动创建父目录） */
    public void upload(Path local, String remote, LogSink sink) {
        try {
            String parent = remote.contains("/") ? remote.substring(0, remote.lastIndexOf('/')) : "/";
            execOrThrow("mkdir -p '" + parent + "'", sink, 60);
            try (SftpClient sftp = org.apache.sshd.sftp.client.SftpClientFactory.instance().createSftpClient(session)) {
                try (var in = Files.newInputStream(local);
                     var out = sftp.write(remote, SftpClient.OpenMode.Write, SftpClient.OpenMode.Create, SftpClient.OpenMode.Truncate)) {
                    in.transferTo(out);
                }
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(500, "SFTP 上传失败: " + remote + " - " + rootMessage(e));
        }
    }

    /** 上传文本内容到远程文件 */
    public void uploadText(String content, String remote, LogSink sink) {
        try {
            Path tmp = Files.createTempFile("forge-upload-", ".tmp");
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                upload(tmp, remote, sink);
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new ApiException(500, "写临时文件失败: " + e.getMessage());
        }
    }

    private static OutputStream lineOut(LogSink sink) {
        return new OutputStream() {
            final ByteArrayOutputStream buf = new ByteArrayOutputStream();
            @Override public void write(int b) {
                if (b == '\n') emit();
                else buf.write(b);
            }
            @Override public void write(byte[] b, int off, int len) {
                for (int i = 0; i < len; i++) write(b[off + i]);
            }
            private void emit() {
                if (buf.size() > 0) {
                    sink.line(new String(buf.toByteArray(), StandardCharsets.UTF_8));
                    buf.reset();
                }
            }
            @Override public void close() { emit(); }
        };
    }

    @Override
    public void close() {
        try { session.close(); } catch (Exception ignore) {}
        try { client.stop(); } catch (Exception ignore) {}
    }

    private static String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        return cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage();
    }
}
