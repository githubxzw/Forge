package com.forge.deploy;

import com.forge.auth.TicketService;
import com.forge.common.ApiException;
import com.forge.domain.Deployment;
import com.forge.domain.TaskStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 部署日志下载链路：ticket 鉴权 + 流式 + 文件名（与 BuildControllerLogTest 对称） */
class DeploymentControllerLogTest {

    @TempDir
    Path dir;

    com.forge.domain.DeploymentRepository repo;
    TicketService ticketService;
    DeploymentController controller;

    @BeforeEach
    void setup() {
        repo = mock(com.forge.domain.DeploymentRepository.class);
        ticketService = new TicketService();
        controller = new DeploymentController(mock(DeployService.class), repo, ticketService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void noCredentialRejectedWith401() {
        Deployment dep = new Deployment();
        dep.setId("d1");
        dep.setStatus(TaskStatus.SUCCESS);
        dep.setLogPath(dir.resolve("deploy-d1.log").toString());
        when(repo.findById("d1")).thenReturn(Optional.of(dep));

        ApiException ex = assertThrows(ApiException.class, () -> controller.log("d1", null));
        assertEquals(401, ex.getCode());
    }

    @Test
    void ticketStreamsFileWithFilename() throws Exception {
        Path logFile = dir.resolve("deploy-d1.log");
        Files.writeString(logFile, "deploy-line\n");
        Deployment dep = new Deployment();
        dep.setId("d1");
        dep.setStatus(TaskStatus.SUCCESS);
        dep.setLogPath(logFile.toString());
        when(repo.findById("d1")).thenReturn(Optional.of(dep));

        String ticket = ticketService.issue();
        ResponseEntity<StreamingResponseBody> resp = controller.log("d1", ticket);
        assertEquals("attachment; filename=deploy-d1.log",
                resp.getHeaders().getFirst("Content-Disposition"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        resp.getBody().writeTo(out);
        assertEquals("deploy-line\n", out.toString(StandardCharsets.UTF_8));
    }
}
