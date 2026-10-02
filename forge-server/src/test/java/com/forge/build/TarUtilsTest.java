package com.forge.build;

import com.forge.infra.log.LogSink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TarUtilsTest {

    @TempDir
    Path dir;

    @Test
    void tarGzAndChecksum() throws Exception {
        Path src = dir.resolve("dist");
        Files.createDirectories(src.resolve("assets"));
        Files.writeString(src.resolve("index.html"), "<h1>hello</h1>");
        Files.writeString(src.resolve("assets/app.css"), "body{}");

        Path target = dir.resolve("out.tar.gz");
        TarUtils.tarGz(src, target, LogSink.NOOP);

        assertTrue(Files.size(target) > 0);
        // 自动创建缺失的父目录
        assertTrue(Files.exists(target));
        assertEquals(64, TarUtils.sha256(target).length());
        // 内容相同 → 校验和一致
        Path target2 = dir.resolve("out2.tar.gz");
        TarUtils.tarGz(src, target2, LogSink.NOOP);
        assertEquals(TarUtils.sha256(target), TarUtils.sha256(target2));
    }
}
