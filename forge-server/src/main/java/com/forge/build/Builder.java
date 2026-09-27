package com.forge.build;

import com.forge.domain.Artifact;
import com.forge.domain.BuildTarget;

/** 构建器 SPI：一类构建目标一个实现 */
public interface Builder {
    BuildTarget target();

    /** 执行构建，返回未持久化的 Artifact（type/version/ref/checksum/size/extra 由实现填充） */
    Artifact build(BuildContext ctx);
}
