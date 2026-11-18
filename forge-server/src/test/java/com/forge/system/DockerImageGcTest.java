package com.forge.system;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 镜像 GC 的未引用判定（纯函数部分） */
class DockerImageGcTest {

    @Test
    void keepsReferencedAndPrunesRest() {
        var unused = DockerImageGc.unreferenced(
                List.of("reg.example.com/demo:main-1", "reg.example.com/demo:main-2", "demo:local-1",
                        "postgres:16", "nginx:alpine"),
                Set.of("reg.example.com/demo:main-2", "nginx:alpine"));
        assertEquals(List.of("reg.example.com/demo:main-1", "demo:local-1", "postgres:16"), unused);
    }

    @Test
    void emptyRefsPrunesEverything() {
        assertEquals(3, DockerImageGc.unreferenced(
                List.of("a:1", "b:2", "c:3"), Set.of()).size());
    }
}
