package com.forge.infra.log;

import com.forge.common.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

/**
 * 日志流中心：写方（构建/部署线程）落盘并广播；读方（WebSocket）先重放文件再接入实时流。
 * 日志结束哨兵行：{@link #END_LINE}
 */
@Slf4j
@Service
public class LogStreamService {
    public static final String END_LINE = "__FORGE_LOG_END__";

    private static class Topic {
        Path file;
        BufferedWriter writer;
        Set<Consumer<String>> listeners = new CopyOnWriteArraySet<>();
        boolean finished;
    }

    private final Map<String, Topic> topics = new ConcurrentHashMap<>();

    /** 获取（或创建）主题的 LogSink */
    public synchronized LogSink sink(String topicName, Path file) {
        Topic topic = topics.computeIfAbsent(topicName, k -> new Topic());
        if (topic.writer == null) {
            topic.file = file;
            try {
                Files.createDirectories(file.getParent());
                topic.writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return text -> {
            synchronized (topic) {
                if (topic.finished) return;
                try {
                    topic.writer.write(text);
                    topic.writer.newLine();
                    topic.writer.flush();
                } catch (IOException e) {
                    log.warn("写日志文件失败: {}", e.getMessage());
                }
                for (Consumer<String> ls : topic.listeners) {
                    try { ls.accept(text); } catch (Exception ignore) {}
                }
            }
        };
    }

    /** 主题结束：哨兵通知所有订阅者并关闭文件 */
    public void finish(String topicName) {
        Topic topic = topics.get(topicName);
        if (topic == null) return;
        synchronized (topic) {
            topic.finished = true;
            for (Consumer<String> ls : topic.listeners) {
                try { ls.accept(END_LINE); } catch (Exception ignore) {}
            }
            topic.listeners.clear();
            if (topic.writer != null) {
                try { topic.writer.close(); } catch (IOException ignore) {}
                topic.writer = null;
            }
        }
        topics.remove(topicName);
    }

    /** 订阅：先重放文件已有内容，再接入实时流。若已结束，重放后立即发送哨兵。返回取消订阅函数 */
    public Runnable subscribe(String topicName, Path file, Consumer<String> onLine) {
        Topic topic = topics.get(topicName);
        if (topic == null || topic.finished) {
            replayFile(file, onLine);
            onLine.accept(END_LINE);
            return () -> {};
        }
        synchronized (topic) {
            replayFile(topic.file, onLine);
            Consumer<String> listener = onLine;
            topic.listeners.add(listener);
            return () -> topic.listeners.remove(listener);
        }
    }

    private void replayFile(Path file, Consumer<String> onLine) {
        if (file == null || !Files.exists(file)) return;
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) onLine.accept(line);
        } catch (IOException e) {
            log.warn("重放日志失败: {}", e.getMessage());
        }
    }
}
