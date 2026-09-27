package com.forge.infra.process;

import com.forge.common.ApiException;
import com.forge.infra.log.LogSink;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** 子进程执行器：参数数组防注入、整树可杀、输出逐行进日志 */
public class ProcessRunner {

    public static int run(List<String> cmd, Path workDir, Map<String, String> env,
                          LogSink sink, Consumer<Process> onStart, long timeoutSec) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (workDir != null) pb.directory(workDir.toFile());
            if (env != null && !env.isEmpty()) pb.environment().putAll(env);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            if (onStart != null) onStart.accept(p);
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sink.line(line);
            }
            boolean done = p.waitFor(timeoutSec, TimeUnit.SECONDS);
            if (!done) {
                killTree(p);
                sink.line("[forge] 命令超时（" + timeoutSec + "s），已终止: " + cmd);
                throw new ApiException(500, "命令执行超时: " + cmd.get(0));
            }
            if (p.exitValue() != 0) {
                sink.line("[forge] 命令失败（exit " + p.exitValue() + "）: " + String.join(" ", cmd));
                throw new ApiException(500, "命令执行失败（exit " + p.exitValue() + "）");
            }
            return p.exitValue();
        } catch (IOException e) {
            throw new ApiException(500, "命令启动失败: " + cmd + " - " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(500, "命令被中断: " + cmd);
        }
    }

    /** 需要捕获 stdout 的小命令（如 git rev-parse），逐行 tee 到 sink */
    public static void runWithCapture(List<String> cmd, Path workDir, LogSink sink,
                                      Consumer<Process> onStart, long timeoutSec) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (workDir != null) pb.directory(workDir.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            if (onStart != null) onStart.accept(p);
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sink.line(line);
            }
            boolean done = p.waitFor(timeoutSec, TimeUnit.SECONDS);
            if (!done) { killTree(p); throw new ApiException(500, "命令执行超时: " + cmd.get(0)); }
            if (p.exitValue() != 0) throw new ApiException(500, "命令执行失败: " + String.join(" ", cmd));
        } catch (IOException e) {
            throw new ApiException(500, "命令启动失败: " + cmd + " - " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(500, "命令被中断");
        }
    }

    /** 带标准输入的小命令（如 docker login --password-stdin），输出静默 */
    public static int runWithStdin(List<String> cmd, String stdin, long timeoutSec) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            Process p = pb.start();
            try (var out = p.getOutputStream()) {
                out.write((stdin + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            boolean done = p.waitFor(timeoutSec, TimeUnit.SECONDS);
            if (!done) { killTree(p); throw new ApiException(500, "命令执行超时: " + cmd.get(0)); }
            if (p.exitValue() != 0) throw new ApiException(500, "命令执行失败（exit " + p.exitValue() + "）");
            return p.exitValue();
        } catch (IOException e) {
            throw new ApiException(500, "命令启动失败: " + cmd + " - " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(500, "命令被中断");
        }
    }

    /** 执行并捕获全部 stdout（不含日志流场景） */
    public static String capture(List<String> cmd, Path workDir, long timeoutSec) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (workDir != null) pb.directory(workDir.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (sb.length() < 8000) sb.append(line).append('\n');
                }
            }
            boolean done = p.waitFor(timeoutSec, TimeUnit.SECONDS);
            if (!done) { killTree(p); throw new ApiException(500, "命令执行超时: " + cmd.get(0)); }
            if (p.exitValue() != 0) throw new ApiException(500, "命令执行失败: " + String.join(" ", cmd));
            return sb.toString();
        } catch (IOException e) {
            throw new ApiException(500, "命令启动失败: " + cmd + " - " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(500, "命令被中断");
        }
    }

    /** 杀掉进程及全部子孙进程 */
    public static void killTree(Process p) {
        p.toHandle().descendants().forEach(ProcessHandle::destroy);
        p.destroy();
        try {
            if (!p.waitFor(5, TimeUnit.SECONDS)) {
                p.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
                p.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
