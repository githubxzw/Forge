package com.forge.config;

import com.forge.domain.AppUser;
import com.forge.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    private final ForgeProperties props;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws IOException {
        Files.createDirectories(props.dataPath());
        Files.createDirectories(props.workPath());
        Files.createDirectories(props.artifactPath());
        Files.createDirectories(props.logPath());

        if (userRepository.count() == 0) {
            AppUser admin = new AppUser();
            admin.setUsername(props.getAdmin().getUsername());
            admin.setPassword(passwordEncoder.encode(props.getAdmin().getPassword()));
            userRepository.save(admin);
            log.info("已创建管理员账号: {}（请尽快修改默认密码）", admin.getUsername());
        }
    }
}
