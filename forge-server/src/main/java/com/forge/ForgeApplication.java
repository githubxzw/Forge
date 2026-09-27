package com.forge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ForgeApplication {
    public static void main(String[] args) {
        // SQLite 在 JPA 初始化时就要求数据目录存在，这里提前创建（优先级：CLI 参数 > 系统属性 > 环境变量 > ~/.forge）
        String dataDir = System.getProperty("forge.data-dir");
        if (dataDir == null || dataDir.isBlank()) {
            for (String a : args) {
                if (a.startsWith("--forge.data-dir=")) dataDir = a.substring("--forge.data-dir=".length());
            }
        }
        if (dataDir == null || dataDir.isBlank()) dataDir = System.getenv("FORGE_DATA_DIR");
        if (dataDir == null || dataDir.isBlank()) dataDir = System.getProperty("user.home") + "/.forge";
        try {
            Files.createDirectories(Path.of(dataDir));
        } catch (IOException ignored) {
        }
        System.setProperty("forge.data-dir", dataDir);
        SpringApplication.run(ForgeApplication.class, args);
    }
}
