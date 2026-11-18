package com.forge.deploy;

import com.forge.common.ApiException;
import com.forge.domain.Environment;
import com.forge.domain.EnvKind;
import com.forge.domain.Project;
import com.forge.infra.log.LogSink;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 健康检查：URL 解析规则 + 单次探测 + 部署后轮询（P2-3 重构后 probe/check 共用一套逻辑） */
class HealthCheckerTest {

    HttpServer server;
    HealthChecker checker = new HealthChecker();
    int port;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", ex -> respond(ex, 200));
        server.createContext("/redirect", ex -> {
            ex.getResponseHeaders().add("Location", "/ok");
            respond(ex, 302);
        });
        server.createContext("/sick", ex -> respond(ex, 500));
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange ex, int code) {
        try (OutputStream os = ex.getResponseBody()) {
            byte[] body = "forge".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(code, body.length);
            os.write(body);
        } catch (Exception ignore) {}
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    @Test
    void probeUrlPassesOn2xx3xx() throws Exception {
        assertTrue(checker.probeUrl(url("/ok")).healthy());
        assertTrue(checker.probeUrl(url("/redirect")).healthy(), "3xx 视为通过");
        assertEquals(500, checker.probeUrl(url("/sick")).statusCode());

        // 连不上（占用一个空闲端口但不监听）→ 码 -1
        try (var socket = new java.net.ServerSocket(0)) {
            int closedPort = socket.getLocalPort(); // 关闭后无人监听
            HealthChecker.Probe miss = checker.probeUrl("http://127.0.0.1:" + closedPort + "/ok");
            assertEquals(-1, miss.statusCode());
        }
    }

    @Test
    void resolveUrlFromProjectConfig() {
        Project p = new Project();
        Environment env = new Environment();
        env.setKind(EnvKind.SSH);
        assertNull(checker.resolveUrl(p, env), "未配置返回 null");

        p.setHealthCheck(Map.of("url", "http://10.0.0.1:8080/healthz"));
        assertEquals("http://10.0.0.1:8080/healthz", checker.resolveUrl(p, env));

        p.setHealthCheck(Map.of("path", "/actuator/health", "port", "9090"));
        env.setConnConfig(Map.of("host", "10.0.0.2"));
        assertEquals("http://10.0.0.2:9090/actuator/health", checker.resolveUrl(p, env));

        // K8S 无显式 url 时不拼路径（helm --wait 已保证就绪）
        env.setKind(EnvKind.K8S);
        assertNull(checker.resolveUrl(p, env));
    }

    @Test
    void checkPollsUntilHealthyOrTimesOut() {
        Project p = new Project();
        p.setHealthCheck(Map.of("url", url("/ok"), "timeoutSec", 5));
        Environment env = new Environment();
        env.setKind(EnvKind.SSH);
        var result = checker.check(p, env, LogSink.NOOP);
        assertEquals(true, result.get("healthy"));

        p.setHealthCheck(Map.of("url", url("/sick"), "timeoutSec", 1));
        assertThrows(ApiException.class, () -> checker.check(p, env, LogSink.NOOP));
    }
}
