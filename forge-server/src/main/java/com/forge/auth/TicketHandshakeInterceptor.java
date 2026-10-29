package com.forge.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/** WS 握手鉴权：?ticket= 一次性消费，取代长效 JWT 进 URL（access log 泄漏面） */
@Component
@RequiredArgsConstructor
public class TicketHandshakeInterceptor implements HandshakeInterceptor {
    private final TicketService ticketService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest req, ServerHttpResponse resp,
                                   WebSocketHandler handler, Map<String, Object> attrs) {
        if (req instanceof ServletServerHttpRequest servlet) {
            return ticketService.consume(servlet.getServletRequest().getParameter("ticket"));
        }
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest req, ServerHttpResponse resp,
                               WebSocketHandler handler, Exception ex) {}
}
