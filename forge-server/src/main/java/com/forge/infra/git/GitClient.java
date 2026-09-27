package com.forge.infra.git;

import com.forge.common.ApiException;
import com.forge.infra.log.LogSink;
import com.forge.infra.process.ProcessRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** 基于 shell git 的代码检出（构建机需安装 git） */
@Component
public class GitClient {

    /** 确保 {dir} 是指定分支的最新代码，返回当前 commit sha */
    public String ensureRepo(String repoUrl, String branch, Path dir, LogSink sink, Consumer<Process> onStart) {
        try {
            if (!Files.exists(dir.resolve(".git"))) {
                Files.createDirectories(dir.getParent());
                run(sink, onStart, dir.getParent(), 1800, "git", "clone", "--branch", branch, repoUrl, dir.getFileName().toString());
            } else {
                run(sink, onStart, dir, 600, "git", "fetch", "origin", "--prune");
                run(sink, onStart, dir, 120, "git", "checkout", "-f", "-B", branch, "origin/" + branch);
            }
            Path gitDir = Files.exists(dir.resolve(".git")) ? dir : dir.getParent();
            return git(sink, dir, "rev-parse", "HEAD").trim();
        } catch (Exception e) {
            if (e instanceof ApiException ae) throw ae;
            throw new ApiException(500, "Git 操作失败: " + e.getMessage());
        }
    }

    public String git(LogSink sink, Path dir, String... args) {
        List<String> cmd = new java.util.ArrayList<>();
        cmd.add("git");
        cmd.addAll(List.of(args));
        StringBuilder captured = new StringBuilder();
        LogSink tee = line -> {
            sink.line(line);
            if (captured.length() < 4000) captured.append(line).append('\n');
        };
        ProcessRunner.runWithCapture(cmd, dir, tee, null, 120);
        return captured.toString();
    }

    private void run(LogSink sink, Consumer<Process> onStart, Path dir, long timeoutSec, String... args) {
        ProcessRunner.run(List.of(args), dir, null, sink, onStart, timeoutSec);
    }
}
