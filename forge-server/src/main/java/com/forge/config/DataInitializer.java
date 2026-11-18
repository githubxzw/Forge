package com.forge.config;

import com.forge.domain.BuildTask;
import com.forge.domain.BuildTaskRepository;
import com.forge.domain.Deployment;
import com.forge.domain.DeploymentRepository;
import com.forge.domain.AppUser;
import com.forge.domain.TaskStatus;
import com.forge.domain.UserRepository;
import com.forge.build.TarUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    private final ForgeProperties props;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BuildTaskRepository buildTaskRepository;
    private final DeploymentRepository deploymentRepository;

    @Override
    public void run(String... args) throws IOException {
        Files.createDirectories(props.dataPath());
        Files.createDirectories(props.workPath());
        Files.createDirectories(props.artifactPath());
        Files.createDirectories(props.logPath());

        recoverDanglingTasks();
        cleanWorkspace();

        if (userRepository.count() == 0) {
            AppUser admin = new AppUser();
            admin.setUsername(props.getAdmin().getUsername());
            admin.setPassword(passwordEncoder.encode(props.getAdmin().getPassword()));
            admin.setRole(com.forge.domain.UserRole.ADMIN);
            userRepository.save(admin);
            log.info("已创建管理员账号: {}（请尽快修改默认密码）", admin.getUsername());
        }
        // 升级回填：role 列加入前创建的用户一律视为管理员
        userRepository.findAll().forEach(u -> {
            if (u.getRole() == null) {
                u.setRole(com.forge.domain.UserRole.ADMIN);
                userRepository.save(u);
            }
        });
    }

    /** 服务重启后，将悬挂的进行中任务标记为中断 */
    private void recoverDanglingTasks() {
        List<TaskStatus> alive = List.of(TaskStatus.PENDING, TaskStatus.RUNNING);
        List<BuildTask> builds = buildTaskRepository.findByStatusIn(alive);
        for (BuildTask t : builds) {
            t.setStatus(TaskStatus.FAILED);
            t.setMessage("服务重启，任务中断");
            t.setFinishedAt(LocalDateTime.now());
            buildTaskRepository.save(t);
        }
        List<Deployment> deps = deploymentRepository.findByStatusIn(alive);
        for (Deployment d : deps) {
            d.setStatus(TaskStatus.FAILED);
            d.setMessage("服务重启，任务中断");
            d.setFinishedAt(LocalDateTime.now());
            deploymentRepository.save(d);
        }
        if (!builds.isEmpty() || !deps.isEmpty()) {
            log.warn("已恢复 {} 个构建、{} 个部署的悬挂状态", builds.size(), deps.size());
        }
    }

    /** 启动时所有任务都已终止，清空工作区残留 */
    private void cleanWorkspace() {
        if (!Files.isDirectory(props.workPath())) return;
        try (Stream<java.nio.file.Path> children = Files.list(props.workPath())) {
            children.forEach(TarUtils::deleteDir);
        } catch (IOException ignore) {}
    }
}
