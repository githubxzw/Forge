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
    private final ForgeProperties props;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 与 REST CORS（SecurityConfig）同一份 FORGE_ALLOWED_ORIGINS 配置：此前硬编码 "*"，改配置不生效
        registry.addHandler(logWebSocketHandler, "/ws/logs/**")
                .addInterceptors(ticketHandshakeInterceptor)
                .setAllowedOriginPatterns(props.getAllowedOrigins().split("\\s*,\\s*"));
    }
}
