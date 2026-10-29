package com.forge.build;

import com.forge.auth.TicketService;
import com.forge.common.ApiException;
import com.forge.domain.BuildTask;
import com.forge.domain.TaskStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 日志下载真实链路回归：鉴权（无凭证 401 / ticket 一次性 / JWT 头放行）、
 * 文件名响应头、流式内容、缺失文件占位。旧代码：window.open 无头 → 401，
 * Files.readString 全量驻堆。
 */
class BuildControllerLogTest {

    @TempDir
    Path dir;

    com.forge.domain.BuildTaskRepository repo;
    TicketService ticketService;
    BuildController controller;

    @BeforeEach
    void setup() {
        repo = mock(com.forge.domain.BuildTaskRepository.class);
        ticketService = new TicketService();
        controller = new BuildController(mock(BuildService.class), repo, ticketService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    private BuildTask taskWithLog(Path logFile) {
        BuildTask t = new BuildTask();
        t.setId("b1");
        t.setStatus(TaskStatus.SUCCESS);
        t.setLogPath(logFile.toString());
        when(repo.findById("b1")).thenReturn(Optional.of(t));
        return t;
    }

    private String bodyOf(ResponseEntity<StreamingResponseBody> resp) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        resp.getBody().writeTo(out);
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void noCredentialRejectedWith401() {
        taskWithLog(dir.resolve("build-b1.log"));
        SecurityContextHolder.clearContext();
        ApiException ex = assertThrows(ApiException.class, () -> controller.log("b1", null));
        assertEquals(401, ex.getCode());
    }

    @Test
    void ticketGrantsOneShotAccessAndStreamsFile() throws Exception {
        Path logFile = dir.resolve("build-b1.log");
        Files.writeString(logFile, "line1\nline2\n");
        taskWithLog(logFile);

        String ticket = ticketService.issue();
        ResponseEntity<StreamingResponseBody> resp = controller.log("b1", ticket);
        assertEquals("attachment; filename=build-b1.log",
                resp.getHeaders().getFirst("Content-Disposition"));
        assertEquals(Files.size(logFile), resp.getHeaders().getContentLength());
        assertEquals("line1\nline2\n", bodyOf(resp));

        // 同一 ticket 第二次必须 401（防重放）
        ApiException ex = assertThrows(ApiException.class, () -> controller.log("b1", ticket));
        assertEquals(401, ex.getCode());
    }

    @Test
    void jwtHeaderGrantsAccessWithoutTicket() {
        Path logFile = dir.resolve("build-b1.log");
        taskWithLog(logFile);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null, List.of()));

        assertDoesNotThrow(() -> controller.log("b1", null));
    }

    @Test
    void missingLogFileStreamsPlaceholder() throws Exception {
        taskWithLog(dir.resolve("build-b1.log")); // 文件不存在

        String ticket = ticketService.issue();
        ResponseEntity<StreamingResponseBody> resp = controller.log("b1", ticket);
        assertEquals("(无日志)", bodyOf(resp).trim());
    }

    @Test
    void unknownTaskRejected() {
        when(repo.findById("nope")).thenReturn(Optional.empty());
        String ticket = ticketService.issue();
        ApiException ex = assertThrows(ApiException.class, () -> controller.log("nope", ticket));
        assertTrue(ex.getMessage().contains("不存在"));
    }
}
