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

/**
 * 子进程执行器：参数数组防注入、整树可杀、输出逐行进日志。
 * 输出由后台守护线程排空，与 waitFor(timeout) 竞速——子进程挂住不关管道时也能按时超时并整树击杀。
 */
public class ProcessRunner {

    public static int run(List<String> cmd, Path workDir, Map<String, String> env,
                          LogSink sink, Consumer<Process> onStart, long timeoutSec) {
        try {
            Process p = start(cmd, workDir, env);
            if (onStart != null) onStart.accept(p);
            Thread drainer = drainAsync(p, sink::line);
            try {
                await(p, cmd, timeoutSec, sink);
                if (p.exitValue() != 0) {
                    sink.line("[forge] 命令失败（exit " + p.exitValue() + "）: " + String.join(" ", cmd));
                    throw new ApiException(500, "命令执行失败（exit " + p.exitValue() + "）");
                }
                return p.exitValue();
            } finally {
                joinQuietly(drainer);
            }
        } catch (IOException e) {
            throw new ApiException(500, "命令启动失败: " + cmd + " - " + e.getMessage());
        }
    }

    /** 需要捕获 stdout 的小命令（如 git rev-parse），逐行 tee 到 sink */
    public static void runWithCapture(List<String> cmd, Path workDir, LogSink sink,
                                      Consumer<Process> onStart, long timeoutSec) {
        try {
            Process p = start(cmd, workDir, null);
            if (onStart != null) onStart.accept(p);
            Thread drainer = drainAsync(p, sink::line);
            try {
                await(p, cmd, timeoutSec, null);
                if (p.exitValue() != 0) throw new ApiException(500, "命令执行失败: " + String.join(" ", cmd));
            } finally {
                joinQuietly(drainer);
            }
        } catch (IOException e) {
            throw new ApiException(500, "命令启动失败: " + cmd + " - " + e.getMessage());
        }
    }

    /** 带标准输入的小命令（如 docker login --password-stdin），输出静默排空防管道写满 */
    public static int runWithStdin(List<String> cmd, String stdin, long timeoutSec) {
        try {
            Process p = start(cmd, null, null);
            Thread drainer = drainAsync(p, line -> { });
            try (var out = p.getOutputStream()) {
                out.write((stdin + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            try {
                await(p, cmd, timeoutSec, null);
                if (p.exitValue() != 0) throw new ApiException(500, "命令执行失败（exit " + p.exitValue() + "）");
                return p.exitValue();
            } finally {
                joinQuietly(drainer);
            }
        } catch (IOException e) {
            throw new ApiException(500, "命令启动失败: " + cmd + " - " + e.getMessage());
        }
    }

    /** 执行并捕获全部 stdout（不含日志流场景） */
    public static String capture(List<String> cmd, Path workDir, long timeoutSec) {
        try {
            Process p = start(cmd, workDir, null);
            StringBuilder sb = new StringBuilder();
            Thread drainer = drainAsync(p, line -> {
                if (sb.length() < 8000) sb.append(line).append('\n');
            });
            try {
                await(p, cmd, timeoutSec, null);
                if (p.exitValue() != 0) throw new ApiException(500, "命令执行失败: " + String.join(" ", cmd));
                return sb.toString();
            } finally {
                joinQuietly(drainer);
            }
        } catch (IOException e) {
            throw new ApiException(500, "命令启动失败: " + cmd + " - " + e.getMessage());
        }
    }

    private static Process start(List<String> cmd, Path workDir, Map<String, String> env) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (workDir != null) pb.directory(workDir.toFile());
        if (env != null && !env.isEmpty()) pb.environment().putAll(env);
        pb.redirectErrorStream(true);
        return pb.start();
    }

    /** 后台排空子进程输出：调用线程只与 waitFor 竞速，永不阻塞在读管道上 */
    private static Thread drainAsync(Process p, Consumer<String> onLine) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) onLine.accept(line);
            } catch (IOException ignore) {
            }
        }, "forge-proc-drain");
        t.setDaemon(true);
        t.start();
        return t;
    }

    /** 等待退出：超时或被中断都先杀整树再抛错（中断不杀会泄漏子进程），超时同时提示到日志 */
    private static void await(Process p, List<String> cmd, long timeoutSec, LogSink sink) {
        boolean done;
        try {
            done = p.waitFor(timeoutSec, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            killTree(p);
            Thread.currentThread().interrupt();
            throw new ApiException(500, "命令被中断: " + cmd);
        }
        if (!done) {
            killTree(p);
            if (sink != null) sink.line("[forge] 命令超时（" + timeoutSec + "s），已终止: " + cmd);
            throw new ApiException(500, "命令执行超时: " + cmd.get(0));
        }
    }

    private static void joinQuietly(Thread drainer) {
        try {
            drainer.join(10_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
