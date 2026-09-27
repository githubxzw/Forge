package com.forge.deploy;

import com.forge.domain.EnvKind;

import java.util.Map;

/** 部署器 SPI：一类环境一个实现 */
public interface Deployer {
    EnvKind kind();

    /** 执行部署，返回结果元数据（写入 Deployment.result） */
    Map<String, Object> deploy(DeployContext ctx);
}
