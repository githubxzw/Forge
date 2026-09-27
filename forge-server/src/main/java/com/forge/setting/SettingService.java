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
        DEFAULTS.put("notifyType", "none");                // none|dingtalk|wecom|feishu|raw
        DEFAULTS.put("notifyWebhook", "");                 // 通知机器人 webhook 地址
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
                .filter(e -> e.getKey().equals("registryPassword") && !e.getValue().isBlank())
                .forEach(e -> e.setValue(MASK));
        return result;
    }

    public void update(Map<String, String> values) {
        values.forEach((k, v) -> {
            if ("registryPassword".equals(k)) {
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

    public boolean registryConfigured() {
        return !get("registry").isBlank();
    }
}
