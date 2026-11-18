package com.forge.build;

import com.forge.domain.BuildTaskRepository;
import com.forge.domain.Project;
import com.forge.domain.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 定时构建到期判定（P1-5）：重启不补跑、错过多窗口只补一次、进行中构建跳过、坏 cron 不拖垮扫描 */
class BuildSchedulerTest {

    ProjectRepository projectRepository;
    BuildTaskRepository buildTaskRepository;
    BuildService buildService;
    BuildScheduler scheduler;

    final ZoneId zone = ZoneId.of("Asia/Shanghai");

    @BeforeEach
    void setup() {
        projectRepository = mock(ProjectRepository.class);
        buildTaskRepository = mock(BuildTaskRepository.class);
        buildService = mock(BuildService.class);
        scheduler = new BuildScheduler(projectRepository, buildTaskRepository, buildService);
        when(buildService.start(anyString(), any(), anyList(), any())).thenReturn(new com.forge.domain.BuildTask());
    }

    private ZonedDateTime at(int hour, int minute) {
        return ZonedDateTime.of(2026, 11, 18, hour, minute, 0, 0, zone);
    }

    @Test
    void notDueWhenJustStarted() {
        // 进程刚启动（fired=null）：以 now 为基准，下一个计划点在明天 → 不触发（重启不误触发）
        assertNull(scheduler.dueTime("0 0 3 * * *", null, at(10, 0)));
        assertNull(scheduler.dueTime("0 0 3 * * *", null, at(3, 0))); // 恰好 3:00:00 整也不算（next 严格大于基准）
    }

    @Test
    void dueAfterScheduledPointPassed() {
        // 上次触发是昨天 3 点，现在今天 10 点 → 今天 3 点已过，触发
        ZonedDateTime due = scheduler.dueTime("0 0 3 * * *", at(3, 0).minusDays(1), at(10, 0));
        assertNotNull(due);
        assertEquals(at(3, 0).toLocalDate(), due.toLocalDate());
        assertEquals(3, due.getHour());
    }

    @Test
    void notDueBeforeScheduledPoint() {
        // 上次触发是今天 3 点，现在 3:30 → 下一个计划点在明天 → 不触发
        assertNull(scheduler.dueTime("0 0 3 * * *", at(3, 0), at(3, 30)));
    }

    @Test
    void multipleMissedWindowsCollapseToMostRecent() {
        // 停机三天（错过 3 个每天 3 点的窗口），恢复后只补跑一次，且收敛到最近一个计划点
        ZonedDateTime due = scheduler.dueTime("0 0 3 * * *", at(3, 0).minusDays(4), at(10, 0));
        assertNotNull(due);
        assertEquals(at(3, 0).toLocalDate(), due.toLocalDate(), "应取今天（最近错过的）而非四天前第一次错过的");
    }

    @Test
    void scanSkipsWhenBuildInFlight() {
        Project p = new Project();
        p.setId("p1");
        p.setCode("demo");
        p.setType(com.forge.domain.ProjectType.BACKEND);
        p.setBuildConfig(Map.of("cron", "0 0 3 * * *"));
        when(projectRepository.findAll()).thenReturn(List.of(p));
        when(buildTaskRepository.existsByProjectIdAndStatusIn(eq("p1"), anyList())).thenReturn(true);
        // 预置 lastFired 到昨天，使当前扫描判定"已到期"
        scheduler.lastFiredForTest().put("p1", at(3, 0).minusDays(1));

        scheduler.scanAt(at(10, 0));

        verify(buildService, never()).start(anyString(), any(), anyList(), any());
    }

    @Test
    void scanFiresWhenDue() {
        Project p = new Project();
        p.setId("p1");
        p.setCode("demo");
        p.setType(com.forge.domain.ProjectType.BACKEND);
        p.setBuildConfig(Map.of("cron", "0 0 3 * * *"));
        when(projectRepository.findAll()).thenReturn(List.of(p));
        when(buildTaskRepository.existsByProjectIdAndStatusIn(eq("p1"), anyList())).thenReturn(false);
        scheduler.lastFiredForTest().put("p1", at(3, 0).minusDays(1));

        scheduler.scanAt(at(10, 0));

        verify(buildService).start(eq("p1"), eq(null), anyList(), eq(com.forge.domain.BuildTrigger.SCHEDULE));
    }

    @Test
    void badCronDoesNotBreakScan() {
        Project p = new Project();
        p.setId("p1");
        p.setCode("demo");
        p.setBuildConfig(Map.of("cron", "not a cron"));
        when(projectRepository.findAll()).thenReturn(List.of(p));

        scheduler.scanAt(at(10, 0)); // 不抛异常即通过（坏表达式仅告警）
        verify(buildService, never()).start(anyString(), any(), anyList(), any());
    }

    @Test
    void cronOfHandlesMissingConfig() {
        assertNull(BuildScheduler.cronOf(new Project()));
        assertNull(BuildScheduler.cronOf(projectWith(Map.of())));
        assertEquals("0 0 3 * * *", BuildScheduler.cronOf(projectWith(Map.of("cron", " 0 0 3 * * * "))));
    }

    private Project projectWith(Map<String, Object> bc) {
        Project p = new Project();
        p.setBuildConfig(bc);
        return p;
    }
}
