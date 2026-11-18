package com.forge.deploy;

import com.forge.domain.Deployment;
import com.forge.domain.DeploymentRepository;
import com.forge.domain.Environment;
import com.forge.domain.EnvironmentRepository;
import com.forge.domain.Project;
import com.forge.domain.ProjectRepository;
import com.forge.domain.TaskStatus;
import com.forge.setting.NotifyService;
import com.forge.setting.SettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 持续健康监控：按 monitorIntervalMin（分钟，0=关闭）周期探测"各环境最近一次成功部署"的服务健康，
 * 状态翻转时才通知（健康→异常告警、异常→恢复通知），稳定态不打扰。状态仅存内存，重启后首次探测重建基线
 * （基线为异常也会立即告警——服务确实挂着）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HealthMonitor {
    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final DeploymentRepository deploymentRepository;
    private final HealthChecker healthChecker;
    private final SettingService settingService;
    private final NotifyService notifyService;

    /** key = projectId:envId；value null=未知（重启后未建立基线） */
    private final Map<String, Boolean> lastHealthy = new ConcurrentHashMap<>();
    private final Map<String, Long> lastCheckedAt = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> unhealthySince = new ConcurrentHashMap<>();

    @Scheduled(fixedDelay = 60_000, initialDelay = 90_000)
    public void tick() {
        int intervalMin = intervalMinutes();
        if (intervalMin <= 0) return; // 关闭
        long intervalMs = intervalMin * 60_000L;
        long now = System.currentTimeMillis();
        for (Target t : targets()) {
            String key = t.projectId() + ":" + t.envId();
            Long last = lastCheckedAt.get(key);
            if (last != null && now - last < intervalMs) continue;
            lastCheckedAt.put(key, now);
            probe(t, key);
        }
    }

    /** 监控目标：项目配了健康检查 + 该环境最近一次部署成功（正在服务的版本） */
    List<Target> targets() {
        List<Target> result = new ArrayList<>();
        List<Project> projects = projectRepository.findAll();
        for (Environment env : environmentRepository.findAll()) {
            for (Project p : projects) {
                Deployment latest = deploymentRepository
                        .findFirstByProjectIdAndEnvironmentIdOrderByCreatedAtDesc(p.getId(), env.getId());
                if (latest == null || latest.getStatus() != TaskStatus.SUCCESS) continue;
                String url = healthChecker.resolveUrl(p, env);
                if (url == null) continue;
                result.add(new Target(p.getId(), p.getName(), p.getCode(), env.getId(), env.getName(), url));
            }
        }
        return result;
    }

    private void probe(Target t, String key) {
        HealthChecker.Probe probe = healthChecker.probeUrl(t.url());
        Boolean previous = lastHealthy.get(key);
        lastHealthy.put(key, probe.healthy());
        if (probe.healthy()) {
            unhealthySince.remove(key);
            // 恢复通知：此前明确处于异常态才发
            if (Boolean.FALSE.equals(previous)) {
                notify("Forge 服务恢复", String.format("项目：%s\n环境：%s\n地址：%s", t.projectName(), t.envName(), t.url()));
            }
        } else {
            if (unhealthySince.get(key) == null) unhealthySince.put(key, LocalDateTime.now());
            // 告警：基线未知（重启后）或此前健康 → 翻转为异常
            if (previous == null || previous) {
                notify("Forge 服务异常", String.format("项目：%s\n环境：%s\n地址：%s\n原因：%s",
                        t.projectName(), t.envName(), t.url(),
                        probe.error() != null ? probe.error().getMessage() : "HTTP " + probe.statusCode()));
            }
        }
    }

    private void notify(String title, String message) {
        String err = notifyService.send(title, message);
        if (err != null) log.warn("监控通知发送失败: {}", err);
    }

    /** GET /api/monitor/status 数据：各目标当前健康态（无部署/无配置的目标不在列） */
    public List<Map<String, Object>> status() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Target t : targets()) {
            String key = t.projectId() + ":" + t.envId();
            Boolean healthy = lastHealthy.get(key);
            if (healthy == null) continue; // 本进程还未探测过，不展示猜测值
            result.add(Map.of(
                    "project", t.projectName(),
                    "projectId", t.projectId(),
                    "environment", t.envName(),
                    "url", t.url(),
                    "healthy", healthy,
                    "unhealthySince", unhealthySince.containsKey(key) ? String.valueOf(unhealthySince.get(key)) : ""));
        }
        return result;
    }

    int intervalMinutes() {
        try {
            return Integer.parseInt(settingService.get("monitorIntervalMin"));
        } catch (Exception e) {
            return 0;
        }
    }

    record Target(String projectId, String projectName, String projectCode,
                  String envId, String envName, String url) {}
}
