package com.forge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class ExecutorConfig {

    @Bean("buildExecutor")
    public ThreadPoolExecutor buildExecutor(ForgeProperties props) {
        return new ThreadPoolExecutor(
                props.getBuildParallelism(), props.getBuildParallelism(),
                60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(500),
                r -> {
                    Thread t = new Thread(r, "build-worker");
                    t.setDaemon(true);
                    return t;
                });
    }

    @Bean("deployExecutor")
    public ThreadPoolExecutor deployExecutor(ForgeProperties props) {
        return new ThreadPoolExecutor(
                props.getDeployParallelism(), props.getDeployParallelism(),
                60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(500),
                r -> {
                    Thread t = new Thread(r, "deploy-worker");
                    t.setDaemon(true);
                    return t;
                });
    }
}
