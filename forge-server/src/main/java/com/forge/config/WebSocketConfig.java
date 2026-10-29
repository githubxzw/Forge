package com.forge.config;

import com.forge.auth.TicketHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {
    private final com.forge.infra.log.LogWebSocketHandler logWebSocketHandler;
    private final TicketHandshakeInterceptor ticketHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(logWebSocketHandler, "/ws/logs/**")
                .addInterceptors(ticketHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
