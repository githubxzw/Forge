package com.forge.setting;

import com.forge.credential.CryptoService;
import com.forge.domain.Setting;
import com.forge.domain.SettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SettingService {
    public static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put("registry", "");                      // 镜像仓库，如 registry.example.com（空=本地镜像名）
        DEFAULTS.put("registryUsername", "");
        DEFAULTS.put("registryPassword", "");              // 加密存储，接口返回时打码
        DEFAULTS.put("npmRegistry", "https://registry.npmjs.org");
        DEFAULTS.put("mavenMirror", "");                   // Maven 镜像（aliyun 等），空=用构建机默认
        DEFAULTS.put("dockerAutoPush", "false");
        DEFAULTS.put("dockerBaseJre", "eclipse-temurin:17-jre");
        DEFAULTS.put("dockerBaseNginx", "nginx:alpine");
        DEFAULTS.put("keepReleases", "5");                 // SSH 部署保留 release 数
        DEFAULTS.put("keepLogDays", "30");                 // 构建/部署日志保留天数，0=永久
        DEFAULTS.put("keepArtifacts", "0");                // 每项目每类型保留制品数，0=永久
        DEFAULTS.put("gitShallow", "true");                // git 浅克隆（--depth 1），加速大仓库检出
        DEFAULTS.put("notifyType", "none");                // none|dingtalk|wecom|feishu|slack|raw|email
        DEFAULTS.put("notifyWebhook", "");                 // 通知机器人 webhook 地址
        DEFAULTS.put("notifyBuild", "true");               // 构建结果也发通知（false=只通知部署）
        DEFAULTS.put("notifyEmailHost", "");               // 邮件 SMTP 主机
        DEFAULTS.put("notifyEmailPort", "465");            // 465=SSL / 587=STARTTLS
        DEFAULTS.put("notifyEmailUser", "");
        DEFAULTS.put("notifyEmailPass", "");               // 加密存储，接口返回时打码
        DEFAULTS.put("notifyEmailFrom", "");               // 发件人地址
        DEFAULTS.put("notifyEmailTo", "");                 // 收件人，逗号分隔
        DEFAULTS.put("notifyEmailSsl", "true");            // true=465 SSL，false=587 STARTTLS
        DEFAULTS.put("monitorIntervalMin", "5");           // 持续健康监控间隔（分钟），0=关闭
        DEFAULTS.put("dockerImageGc", "true");             // 每日清理本地未引用/悬挂 docker 镜像
    }

    private static final String MASK = "******";

    private final SettingRepository repository;
    private final CryptoService cryptoService;

    public String get(String key) {
        return repository.findById(key).map(Setting::getValue)
                .filter(v -> !v.isBlank())
                .orElse(DEFAULTS.getOrDefault(key, ""));
    }

    public Map<String, String> getAll() {
        Map<String, String> result = new LinkedHashMap<>();
        DEFAULTS.forEach((k, v) -> result.put(k, get(k)));
        result.entrySet().stream()
                .filter(e -> (e.getKey().equals("registryPassword") || e.getKey().equals("notifyEmailPass"))
                        && !e.getValue().isBlank())
                .forEach(e -> e.setValue(MASK));
        return result;
    }

    public void update(Map<String, String> values) {
        values.forEach((k, v) -> {
            if ("registryPassword".equals(k) || "notifyEmailPass".equals(k)) {
                if (v == null || v.isBlank() || MASK.equals(v)) return; // 保留原值
                v = cryptoService.encryptText(v);
            }
            Setting s = repository.findById(k).orElseGet(() -> {
                Setting n = new Setting();
                n.setKey(k);
                return n;
            });
            s.setValue(v);
            repository.save(s);
        });
    }

    /** 取出 registry 明文密码（docker login 用） */
    public String registryPasswordPlain() {
        String stored = get("registryPassword");
        if (stored.isBlank()) return "";
        if (stored.startsWith("enc:v1:")) return cryptoService.decryptText(stored);
        return stored;
    }

    /** 取出邮件 SMTP 明文密码 */
    public String emailPasswordPlain() {
        String stored = get("notifyEmailPass");
        if (stored.isBlank()) return "";
        if (stored.startsWith("enc:v1:")) return cryptoService.decryptText(stored);
        return stored;
    }

    public boolean registryConfigured() {
        return !get("registry").isBlank();
    }
}
