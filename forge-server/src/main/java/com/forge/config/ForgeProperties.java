package com.forge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "forge")
public class ForgeProperties {
    private String dataDir;
    private String workDir;
    private String artifactDir;
    private String logDir;
    private int buildParallelism = 2;
    private int deployParallelism = 2;
    private int jwtExpireHours = 24;
    /** CORS 允许来源，逗号分隔；公网部署务必改为具体域名 */
    private String allowedOrigins = "*";
    private Admin admin = new Admin();

    @Data
    public static class Admin {
        private String username = "admin";
        private String password = "admin123";
    }

    public Path dataPath() { return Path.of(dataDir); }
    public Path workPath() { return Path.of(workDir); }
    public Path artifactPath() { return Path.of(artifactDir); }
    public Path logPath() { return Path.of(logDir); }
    public Duration jwtExpire() { return Duration.ofHours(jwtExpireHours); }
}
