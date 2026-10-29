package com.forge.infra.process;

import com.forge.common.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 旧实现「readLine 读到 EOF 才 waitFor」，子进程挂住不关 stdout 时 timeout 参数形同虚设、
 * 调用线程永久阻塞。本类核心断言：超时必须在数秒内触发并杀掉子进程。
 */
@EnabledOnOs({OS.MAC, OS.LINUX})
class ProcessRunnerTest {

    private static List<String> sh(String script) {
        return List.of("sh", "-c", script);
    }

    @Test
    void runTimeoutKillsHangingChildQuickly() {
        List<String> lines = new ArrayList<>();
        long t0 = System.currentTimeMillis();
        ApiException ex = assertThrows(ApiException.class,
                () -> ProcessRunner.run(sh("echo hi; sleep 30"), null, null, lines::add, null, 2));
        long elapsed = System.currentTimeMillis() - t0;
        assertTrue(elapsed < 10_000, "超时应数秒内触发，实际 " + elapsed + "ms");
        assertTrue(ex.getMessage().contains("命令执行超时"), ex.getMessage());
        assertTrue(lines.contains("hi"), "超时前的输出仍应流式送达");
    }

    @Test
    void runSuccessStreamsLines() {
        List<String> lines = new ArrayList<>();
        int code = ProcessRunner.run(sh("echo one; echo two"), null, null, lines::add, null, 10);
        assertEquals(0, code);
        assertEquals(List.of("one", "two"), lines);
    }

    @Test
    void runNonZeroExitThrows() {
        List<String> lines = new ArrayList<>();
        ApiException ex = assertThrows(ApiException.class,
                () -> ProcessRunner.run(sh("echo bad; exit 3"), null, null, lines::add, null, 10));
        assertTrue(ex.getMessage().contains("exit 3"), ex.getMessage());
        assertTrue(lines.contains("bad"));
    }

    @Test
    void runWithCaptureTimeout() {
        long t0 = System.currentTimeMillis();
        assertThrows(ApiException.class,
                () -> ProcessRunner.runWithCapture(sh("echo out; sleep 30"), null, l -> {}, null, 2));
        assertTrue(System.currentTimeMillis() - t0 < 10_000);
    }

    @Test
    void runWithCaptureTeesOutputAndFailsOnExit() {
        List<String> lines = new ArrayList<>();
        ApiException ex = assertThrows(ApiException.class,
                () -> ProcessRunner.runWithCapture(sh("echo out; exit 2"), null, lines::add, null, 10));
        assertTrue(ex.getMessage().contains("out") || ex.getMessage().contains("exit"), ex.getMessage());
        assertEquals(List.of("out"), lines);
    }

    @Test
    void runWithStdinPassesValue() {
        assertEquals(0, ProcessRunner.runWithStdin(sh("read v; [ \"$v\" = hello ]"), "hello", 10));
        ApiException ex = assertThrows(ApiException.class,
                () -> ProcessRunner.runWithStdin(sh("read v; [ \"$v\" = hello ]"), "world", 10));
        assertTrue(ex.getMessage().contains("exit"), ex.getMessage());
    }

    @Test
    void runWithStdinTimeoutDespiteSilentChild() {
        // 子进程既不读 stdin 也不退出：写入端不能在 waitFor 之前永久阻塞
        long t0 = System.currentTimeMillis();
        assertThrows(ApiException.class,
                () -> ProcessRunner.runWithStdin(sh("sleep 30"), "x", 2));
        assertTrue(System.currentTimeMillis() - t0 < 10_000);
    }

    @Test
    void captureReturnsStdout() {
        assertEquals("captured", ProcessRunner.capture(sh("echo captured"), null, 10).trim());
    }

    @Test
    void captureTimeout() {
        long t0 = System.currentTimeMillis();
        assertThrows(ApiException.class,
                () -> ProcessRunner.capture(sh("echo x; sleep 30"), null, 2));
        assertTrue(System.currentTimeMillis() - t0 < 10_000);
    }

    @Test
    void onStartReceivesProcess() {
        AtomicBoolean called = new AtomicBoolean(false);
        ProcessRunner.run(sh("exit 0"), null, null, l -> {}, p -> called.set(p != null), 10);
        assertTrue(called.get());
    }
}
