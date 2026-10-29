package com.forge.infra.log;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 旧 subscribe 见 topic 不存在就 replay+END：排队中的任务（sink 尚未创建）被误判「已结束」。
 * 新语义：subscribe 带 terminated 供应者，占位 topic 等待任务真正开始或终结。
 */
class LogStreamServiceTest {

    @TempDir
    Path dir;

    @Test
    void queuedTaskDoesNotEndImmediately() {
        LogStreamService svc = new LogStreamService();
        Path file = dir.resolve("build-x.log");
        List<String> received = new CopyOnWriteArrayList<>();
        AtomicBoolean ended = new AtomicBoolean(false);

        svc.subscribe("build:x", file, l -> {
            if (LogStreamService.END_LINE.equals(l)) ended.set(true);
            else received.add(l);
        }, () -> false);

        assertFalse(ended.get(), "任务未终结（排队中）不应立即收到 END");
        assertTrue(received.isEmpty());

        // 任务开始：sink 创建后实时推送
        LogSink sink = svc.sink("build:x", file);
        sink.line("hello");
        assertTrue(received.contains("hello"), "占位订阅应接入实时流: " + received);

        svc.finish("build:x");
        assertTrue(ended.get(), "finish 后订阅者应收到 END");
    }

    @Test
    void terminatedTaskReplaysFileThenEnds() throws Exception {
        LogStreamService svc = new LogStreamService();
        Path file = dir.resolve("deploy-y.log");
        Files.writeString(file, "old-line\n");
        List<String> received = new CopyOnWriteArrayList<>();

        svc.subscribe("deploy:y", file, received::add, () -> true);

        assertEquals(List.of("old-line", LogStreamService.END_LINE), received);
    }

    @Test
    void terminatedDuringQueueSendsEndAndCleansPlaceholder() {
        LogStreamService svc = new LogStreamService();
        Path file = dir.resolve("build-z.log");
        List<String> received = new CopyOnWriteArrayList<>();

        AtomicBoolean terminated = new AtomicBoolean(false);
        svc.subscribe("build:z", file, received::add, terminated::get);
        terminated.set(true);

        // 任务取消（cancel(PENDING) → finish）：订阅者收到 END
        svc.finish("build:z");
        assertTrue(received.contains(LogStreamService.END_LINE));
    }

    @Test
    void unsubStopsLivePush() {
        LogStreamService svc = new LogStreamService();
        Path file = dir.resolve("build-w.log");
        List<String> received = new CopyOnWriteArrayList<>();
        Runnable unsub = svc.subscribe("build:w", file, received::add, () -> false);

        LogSink sink = svc.sink("build:w", file);
        sink.line("a");
        unsub.run();
        sink.line("b");

        assertTrue(received.contains("a"));
        assertFalse(received.contains("b"), "取消订阅后不应再收到推送");
        svc.finish("build:w");
    }

    @Test
    void lateSubscriberAfterFinishGetsReplayAndEnd() {
        LogStreamService svc = new LogStreamService();
        Path file = dir.resolve("build-v.log");
        LogSink sink = svc.sink("build:v", file);
        sink.line("wrote-before-end");
        svc.finish("build:v");

        List<String> received = new CopyOnWriteArrayList<>();
        svc.subscribe("build:v", file, received::add, () -> true);
        assertEquals(List.of("wrote-before-end", LogStreamService.END_LINE), received);
    }
}
