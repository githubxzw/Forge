package com.forge.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildServiceTest {

    @Test
    void versionFormat() {
        String v = BuildService.genVersion("Main/Feature#1", "abcdef1234567890");
        // 分支非法字符折叠为中划线 + 时间戳(yyyyMMddHHmmss 14位) + commit 前 7 位 + 4 位随机后缀（防同分钟碰撞）
        assertTrue(v.matches("main-feature-1-\\d{14}-abcdef1-[0-9a-f]{4}"), v);
    }

    @Test
    void longBranchTruncatedTo20() {
        String v = BuildService.genVersion("a-very-long-branch-name-exceeding", "0123456789abcd");
        String branchPart = v.split("-\\d{14}-")[0];
        assertTrue(branchPart.length() <= 20, v);
        assertTrue(v.matches(".*-0123456-[0-9a-f]{4}"), v);
    }

    @Test
    void shortCommitFallsBackToLocal() {
        // 真实 git commit 都是 40 位；过短的值（伪造/异常场景）统一回退为 local
        String v = BuildService.genVersion("main", "abc");
        assertTrue(v.matches("main-\\d{14}-local-[0-9a-f]{4}"), v);
        assertEquals("local", BuildService.genVersion("main", null).split("-")[2]);
    }

    @Test
    void sameMinuteBuildsDoNotCollide() {
        String a = BuildService.genVersion("main", "abcdef1234567890");
        String b = BuildService.genVersion("main", "abcdef1234567890");
        assertNotEquals(a, b); // 随机后缀保证连发构建不互相覆盖制品
    }
}
