package com.forge.auth;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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

    /**
     * 下载接口鉴权：请求已带有效 JWT 头（SecurityContext 已认证）直接放行；
     * 否则必须携带有效一次性 ticket。二者皆无 → 401。
     * （window.open 无法带 Authorization 头，ticket 即为此类通道准备）
     */
    public void requireAuthOrTicket(String ticket) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        boolean headerAuthed = auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
        if (!headerAuthed && !consume(ticket)) {
            throw new com.forge.common.ApiException(401, "下载凭证无效或已过期");
        }
    }
}
