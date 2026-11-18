package com.forge.build;

import com.forge.domain.BuildTaskRepository;
import com.forge.domain.BuildTrigger;
import com.forge.domain.Project;
import com.forge.domain.ProjectRepository;
import com.forge.domain.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 定时构建：项目 buildConfig.cron 配置 Spring cron 表达式（如 "0 0 3 * * *" 每天凌晨 3 点），
 * 每 30s 扫描一次；停机错过的窗口只在恢复后补跑一次，进行中的构建自动跳过。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BuildScheduler {
    private final ProjectRepository projectRepository;
    private final BuildTaskRepository buildTaskRepository;
    private final BuildService buildService;

    /** 项目 id → 上次已触发的 cron 计划时间（重启后重置，恢复期最多补跑一次） */
    private final Map<String, ZonedDateTime> lastFired = new ConcurrentHashMap<>();

    @Scheduled(fixedDelay = 30_000, initialDelay = 20_000)
    public void scan() {
        scanAt(ZonedDateTime.now());
    }

    /** 供单测注入时钟的扫描入口 */
    void scanAt(ZonedDateTime now) {
        List<Project> projects = projectRepository.findAll();
        for (Project project : projects) {
            try {
                String cron = cronOf(project);
                if (cron == null) continue;
                ZonedDateTime due = dueTime(cron, lastFired.get(project.getId()), now);
                if (due == null) continue;
                lastFired.put(project.getId(), due);
                if (buildTaskRepository.existsByProjectIdAndStatusIn(
                        project.getId(), List.of(TaskStatus.PENDING, TaskStatus.RUNNING))) {
                    log.info("定时构建跳过（已有进行中构建）: project={}", project.getCode());
                    continue;
                }
                buildService.start(project.getId(), null, WebhookController.defaultTargetsOf(project), BuildTrigger.SCHEDULE);
                log.info("定时构建已触发: project={} cron={}", project.getCode(), cron);
            } catch (Exception e) {
                // 单个项目出错不影响其他项目调度（cron 配错是用户输入问题，不能拖垮扫描线程）
                log.warn("定时构建触发失败: project={} err={}", project.getCode(), e.getMessage());
            }
        }
    }

    /** 供单测预置/检查 lastFired 状态 */
    Map<String, ZonedDateTime> lastFiredForTest() {
        return lastFired;
    }

    /**
     * 到期判断：上次触发点之后最近一个计划时刻 <= now 才触发；错过多个窗口时收敛到最近一个（只补跑一次）。
     * fired 为 null（进程刚启动）时以 now 为基准——重启不补跑历史窗口，从下一个计划点正常触发。
     */
    ZonedDateTime dueTime(String cron, ZonedDateTime fired, ZonedDateTime now) {
        CronExpression expr = CronExpression.parse(cron);
        ZonedDateTime next = expr.next(fired == null ? now : fired);
        if (next == null || next.isAfter(now)) return null;
        while (true) {
            ZonedDateTime further = expr.next(next);
            if (further == null || further.isAfter(now)) return next;
            next = further;
        }
    }

    static String cronOf(Project project) {
        Object cron = project.getBuildConfig() == null ? null : project.getBuildConfig().get("cron");
        if (cron instanceof String s && !s.isBlank()) return s.trim();
        return null;
    }
}
