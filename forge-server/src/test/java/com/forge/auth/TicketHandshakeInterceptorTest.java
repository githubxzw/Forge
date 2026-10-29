package com.forge.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** WS 握手改用一次性 ticket（?ticket=），不再让长效 JWT 进 URL/access log */
class TicketHandshakeInterceptorTest {

    private final TicketService ticketService = new TicketService(60_000);
    private final TicketHandshakeInterceptor interceptor = new TicketHandshakeInterceptor(ticketService);

    private boolean handshake(String ticket) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/ws/logs/build/x");
        if (ticket != null) req.setParameter("ticket", ticket);
        return interceptor.beforeHandshake(
                new ServletServerHttpRequest(req), new ServletServerHttpResponse(new MockHttpServletResponse()), null, null);
    }

    @Test
    void validTicketPassesOnce() {
        String t = ticketService.issue();
        assertTrue(handshake(t), "有效 ticket 握手应通过");
        assertFalse(handshake(t), "同一 ticket 二次握手必须拒绝（防重放）");
    }

    @Test
    void missingOrBogusTicketRejected() {
        assertFalse(handshake(null));
        assertFalse(handshake("not-a-ticket"));
    }
}
