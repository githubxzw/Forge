package com.forge.auth;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一次性短效 ticket：供无法携带 Authorization 头的通道（WS 握手、文件下载）换取访问权。
 * JWT 换 ticket（60s TTL），用过即焚——URL 里即使落入 access log 也已失效。
 */
@Service
public class TicketService {
    private final ConcurrentHashMap<String, Long> tickets = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final long ttlMs;

    public TicketService() { this(60_000); }

    /** 包级构造：测试可注入短 TTL */
    TicketService(long ttlMs) { this.ttlMs = ttlMs; }

    public String issue() {
        long now = System.currentTimeMillis();
        tickets.entrySet().removeIf(e -> e.getValue() < now); // 顺手清理过期项
        byte[] buf = new byte[32];
        random.nextBytes(buf);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        tickets.put(ticket, now + ttlMs);
        return ticket;
    }

    /** 一次性消费：原子移除后校验有效期 */
    public boolean consume(String ticket) {
        if (ticket == null || ticket.isBlank()) return false;
        Long expireAt = tickets.remove(ticket);
        return expireAt != null && expireAt >= System.currentTimeMillis();
    }
}
