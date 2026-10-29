package com.forge.infra.log;

import com.forge.config.ForgeProperties;
import com.forge.domain.BuildTaskRepository;
import com.forge.domain.DeploymentRepository;
import com.forge.domain.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 日志 WebSocket：/ws/logs/build/{taskId} 或 /ws/logs/deploy/{deploymentId}?ticket=xxx
 * 连接即重放历史并接收实时推送，结束时发送 {@link LogStreamService#END_LINE} 后关闭。
 * 排队中的任务（PENDING）同样可订阅：terminated 供应者告知任务是否已终结，占位 topic 等它开始。
 */
@Component
@RequiredArgsConstructor
public class LogWebSocketHandler extends TextWebSocketHandler {
    private final LogStreamService logStreamService;
    private final ForgeProperties props;
    private final BuildTaskRepository buildTaskRepository;
    private final DeploymentRepository deploymentRepository;

    private final Map<String, Runnable> unsubs = new ConcurrentHashMap<>();

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 客户端消息仅用于保活，忽略
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String path = session.getUri().getPath(); // /ws/logs/build/{id}
        String[] parts = path.split("/");
        if (parts.length < 5) { session.close(CloseStatus.BAD_DATA); return; }
        String kind = parts[3];
        String id = parts[4];
        if (!kind.equals("build") && !kind.equals("deploy")) { session.close(CloseStatus.BAD_DATA); return; }

        Path file = "build".equals(kind)
                ? props.logPath().resolve("build-" + id + ".log")
                : props.logPath().resolve("deploy-" + id + ".log");

        Runnable unsub = logStreamService.subscribe(kind + ":" + id, file, line -> {
            try {
                if (session.isOpen()) session.sendMessage(new TextMessage(line));
            } catch (Exception e) {
                Runnable u = unsubs.remove(session.getId());
                if (u != null) u.run();
            }
        }, () -> terminated("build".equals(kind), id));
        unsubs.put(session.getId(), unsub);
    }

    /** 查任务表判断是否终结：记录不存在（未落库的脏 id）视为已终结，避免订阅永不结束 */
    private boolean terminated(boolean isBuild, String id) {
        if (isBuild) {
            return buildTaskRepository.findById(id)
                    .map(t -> t.getStatus() != TaskStatus.PENDING && t.getStatus() != TaskStatus.RUNNING)
                    .orElse(true);
        }
        return deploymentRepository.findById(id)
                .map(d -> d.getStatus() != TaskStatus.PENDING && d.getStatus() != TaskStatus.RUNNING)
                .orElse(true);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Runnable u = unsubs.remove(session.getId());
        if (u != null) u.run();
    }
}
