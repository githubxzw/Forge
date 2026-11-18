package com.forge.setting;

import com.forge.common.JsonUtils;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;

/** 构建/部署结果通知：钉钉 / 企业微信 / 飞书 / Slack 自定义机器人、通用 JSON、邮件 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyService {
    private final SettingService settingService;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    /** 发送通知。返回 null 表示成功，否则返回错误描述（不抛异常，避免影响主流程） */
    public String send(String title, String message) {
        try {
            String type = settingService.get("notifyType");
            if (type == null || "none".equals(type)) return null;
            if ("email".equals(type)) return sendEmail(title, message);
            String url = settingService.get("notifyWebhook");
            if (url == null || url.isBlank()) return null;
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload(type, title, message)))
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

    /** 各渠道消息体（包内可见便于单测断言格式） */
    static String payload(String type, String title, String message) {
        return switch (type) {
            case "dingtalk", "wecom" -> "{\"msgtype\":\"text\",\"text\":{\"content\":"
                    + JsonUtils.toJson(title + "\n" + message) + "}}";
            case "feishu" -> "{\"msg_type\":\"text\",\"content\":{\"text\":"
                    + JsonUtils.toJson(title + "\n" + message) + "}}";
            case "slack" -> "{\"text\":" + JsonUtils.toJson(title + "\n" + message) + "}";
            default -> "{\"title\":" + JsonUtils.toJson(title) + ",\"message\":" + JsonUtils.toJson(message) + "}";
        };
    }

    /** 邮件渠道：SMTP 参数来自系统设置（DB），每次发送现建 sender——通知频率低，不值得常驻连接 */
    private String sendEmail(String title, String message) {
        try {
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(settingService.get("notifyEmailHost"));
            sender.setPort(Integer.parseInt(settingService.get("notifyEmailPort")));
            String user = settingService.get("notifyEmailUser");
            String pass = settingService.emailPasswordPlain();
            if (!user.isBlank() && !pass.isBlank()) {
                sender.setUsername(user);
                sender.setPassword(pass);
            }
            Properties props = sender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", String.valueOf(!user.isBlank()));
            boolean ssl = "true".equalsIgnoreCase(settingService.get("notifyEmailSsl"));
            props.put("mail.smtp.ssl.enable", String.valueOf(ssl));
            props.put("mail.smtp.starttls.enable", String.valueOf(!ssl));
            props.put("mail.smtp.connectiontimeout", "5000");
            props.put("mail.smtp.timeout", "10000");

            MimeMessage mime = sender.createMimeMessage();
            mime.setFrom(new InternetAddress(settingService.get("notifyEmailFrom")));
            mime.setRecipients(MimeMessage.RecipientType.TO,
                    InternetAddress.parse(settingService.get("notifyEmailTo")));
            mime.setSubject(title, StandardCharsets.UTF_8.name());
            mime.setText(message, StandardCharsets.UTF_8.name());
            sender.send(mime);
            return null;
        } catch (Exception e) {
            log.warn("邮件通知发送失败: {}", e.getMessage());
            return e.getMessage();
        }
    }
}
