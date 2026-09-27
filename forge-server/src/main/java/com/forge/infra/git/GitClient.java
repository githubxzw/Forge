package com.forge.infra.git;

import com.forge.common.ApiException;
import com.forge.infra.log.LogSink;
import com.forge.infra.process.ProcessRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** 基于 shell git 的代码检出（构建机需安装 git），支持私有仓库凭据 */
@Component
public class GitClient {

    /** 确保 {dir} 是指定分支的最新代码，返回当前 commit sha */
    public String ensureRepo(String repoUrl, String branch, Path dir, LogSink sink,
                             Consumer<Process> onStart, GitAuth auth) {
        GitAuth a = auth == null ? GitAuth.NONE : auth;
        try {
            if (!Files.exists(dir.resolve(".git"))) {
                Files.createDirectories(dir.getParent());
                run(sink, onStart, dir.getParent(), 1800, a, "git", "clone", "--branch", branch, repoUrl, dir.getFileName().toString());
            } else {
                run(sink, onStart, dir, 600, a, "git", "fetch", "origin", "--prune");
                run(sink, onStart, dir, 120, a, "git", "checkout", "-f", "-B", branch, "origin/" + branch);
            }
            Path gitDir = Files.exists(dir.resolve(".git")) ? dir : dir.getParent();
            return git(sink, dir, a, "rev-parse", "HEAD").trim();
        } catch (Exception e) {
            if (e instanceof ApiException ae) throw ae;
            throw new ApiException(500, "Git 操作失败: " + e.getMessage());
        }
    }

    private void run(LogSink sink, Consumer<Process> onStart, Path dir, long timeoutSec, GitAuth auth, String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add(args[0]);
        cmd.addAll(auth.args());
        for (int i = 1; i < args.length; i++) cmd.add(args[i]);
        ProcessRunner.run(cmd, dir, auth.env(), sink, onStart, timeoutSec);
    }

    private String git(LogSink sink, Path dir, GitAuth auth, String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.addAll(auth.args());
        cmd.addAll(List.of(args));
        StringBuilder captured = new StringBuilder();
        LogSink tee = line -> {
            sink.line(line);
            if (captured.length() < 4000) captured.append(line).append('\n');
        };
        ProcessRunner.runWithCapture(cmd, dir, tee, null, 120);
        return captured.toString();
    }
}
