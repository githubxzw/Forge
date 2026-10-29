package com.forge.auth;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 一次性短效 ticket：WS 握手与日志下载共用，替代长效 JWT 走 URL 的泄漏面 */
class TicketServiceTest {

    @Test
    void consumeIsOneShot() {
        TicketService svc = new TicketService(60_000);
        String t = svc.issue();
        assertTrue(svc.consume(t), "首次消费应成功");
        assertFalse(svc.consume(t), "ticket 一次性，二次消费必须失败");
        assertFalse(svc.consume("bogus"));
        assertFalse(svc.consume(null));
        assertFalse(svc.consume(""));
    }

    @Test
    void expiredTicketRejected() throws InterruptedException {
        TicketService svc = new TicketService(30);
        String t = svc.issue();
        Thread.sleep(80);
        assertFalse(svc.consume(t), "过期 ticket 必须失效");
    }

    @Test
    void ticketsAreUnique() {
        TicketService svc = new TicketService(60_000);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) seen.add(svc.issue());
        assertEquals(100, seen.size());
    }
}
