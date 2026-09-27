package com.forge.infra.log;

import com.forge.config.ForgeProperties;
import com.forge.setting.SettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

/** 按保留天数定时清理构建/部署日志（每天 03:30 执行；keepLogDays=0 表示永久保留） */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogCleanup {
    private final ForgeProperties props;
    private final SettingService settingService;

    @Scheduled(cron = "0 30 3 * * ?")
    public void clean() {
        int days;
        try {
            days = Integer.parseInt(settingService.get("keepLogDays"));
        } catch (Exception e) {
            return;
        }
        if (days <= 0) return;
        Path logDir = props.logPath();
        if (!Files.isDirectory(logDir)) return;
        FileTime cutoff = FileTime.from(Instant.now().minus(days, ChronoUnit.DAYS));
        int deleted = 0;
        try (Stream<Path> files = Files.list(logDir)) {
            for (Path p : files.filter(Files::isRegularFile).toList()) {
                String name = p.getFileName().toString();
                if (!(name.startsWith("build-") || name.startsWith("deploy-"))) continue;
                if (!name.endsWith(".log")) continue;
                try {
                    if (Files.getLastModifiedTime(p).compareTo(cutoff) < 0) {
                        Files.deleteIfExists(p);
                        deleted++;
                    }
                } catch (IOException ignore) {}
            }
        } catch (IOException e) {
            log.warn("日志清理失败: {}", e.getMessage());
            return;
        }
        if (deleted > 0) log.info("日志清理完成，删除 {} 个超过 {} 天的日志文件", deleted, days);
    }
}
