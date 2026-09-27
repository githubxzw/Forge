package com.forge.deploy;

import com.forge.domain.Environment;
import com.forge.domain.Project;
import com.forge.infra.log.LogSink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** 部署后健康检查：按项目 healthCheck 配置轮询 HTTP */
@Slf4j
@Component
@RequiredArgsConstructor
public class HealthChecker {

    /**
     * healthCheck 配置：{url} 或 {path, port}（host 取环境主机）；K8S 环境无 url 时跳过（helm --wait 已保证就绪）。
     * 未配置健康检查时返回 {skipped: true}
     */
    public Map<String, Object> check(Project project, Environment env, LogSink log) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> hc = project.getHealthCheck();
        String url = hc == null ? null : com.forge.project.EnvironmentService.str(hc, "url");
        if (url == null || url.isBlank()) {
            String path = hc == null ? null : com.forge.project.EnvironmentService.str(hc, "path");
            String port = hc == null ? null : com.forge.project.EnvironmentService.str(hc, "port");
            if (path != null && !path.isBlank() && env.getKind() != com.forge.domain.EnvKind.K8S) {
                String host = com.forge.project.EnvironmentService.str(env.getConnConfig(), "host");
                if (host != null && !host.isBlank()) {
                    url = "http://" + host + ":" + (port == null || port.isBlank() ? "80" : port) + path;
                }
            }
        }
        if (url == null || url.isBlank()) {
            result.put("skipped", true);
            log.line("[forge] 未配置健康检查，跳过");
            return result;
        }

        int timeoutSec = 300;
        int intervalSec = 5;
        if (hc.get("timeoutSec") instanceof Number n) timeoutSec = n.intValue();
        log.line("[forge] 健康检查: " + url + "（最长 " + timeoutSec + "s）");

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        long deadline = System.currentTimeMillis() + timeoutSec * 1000L;
        int lastCode = -1;
        Exception lastError = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpResponse<Void> resp = client.send(
                        HttpRequest.newBuilder(URI.create(url)).GET().build(),
                        HttpResponse.BodyHandlers.discarding());
                lastCode = resp.statusCode();
                if (lastCode >= 200 && lastCode < 400) {
                    result.put("healthy", true);
                    result.put("url", url);
                    result.put("statusCode", lastCode);
                    log.line("[forge] 健康检查通过（HTTP " + lastCode + "）");
                    return result;
                }
            } catch (Exception e) {
                lastError = e;
                lastCode = -1;
            }
            try { Thread.sleep(intervalSec * 1000L); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        result.put("healthy", false);
        result.put("url", url);
        result.put("statusCode", lastCode);
        if (lastError != null) result.put("error", lastError.getMessage());
        throw new com.forge.common.ApiException(500, "健康检查失败: " + url + (lastCode > 0 ? "（HTTP " + lastCode + "）" : ""));
    }
}
