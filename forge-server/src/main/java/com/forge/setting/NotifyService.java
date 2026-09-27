package com.forge.setting;

import com.forge.common.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** 部署结果通知：钉钉 / 企业微信 / 飞书 自定义机器人，或通用 JSON */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyService {
    private final SettingService settingService;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    /** 发送通知。返回 null 表示成功，否则返回错误描述（不抛异常，避免影响主流程） */
    public String send(String title, String message) {
        try {
            String url = settingService.get("notifyWebhook");
            String type = settingService.get("notifyType");
            if (url == null || url.isBlank() || type == null || "none".equals(type)) return null;
            String payload = switch (type) {
                case "dingtalk", "wecom" -> "{\"msgtype\":\"text\",\"text\":{\"content\":"
                        + JsonUtils.toJson(title + "\n" + message) + "}}";
                case "feishu" -> "{\"msg_type\":\"text\",\"content\":{\"text\":"
                        + JsonUtils.toJson(title + "\n" + message) + "}}";
                default -> "{\"title\":" + JsonUtils.toJson(title) + ",\"message\":" + JsonUtils.toJson(message) + "}";
            };
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                log.warn("通知发送失败: HTTP {} {}", resp.statusCode(), resp.body());
                return "HTTP " + resp.statusCode() + (resp.body() == null ? "" : " " + resp.body());
            }
            return null;
        } catch (Exception e) {
            log.warn("通知发送失败: {}", e.getMessage());
            return e.getMessage();
        }
    }
}
