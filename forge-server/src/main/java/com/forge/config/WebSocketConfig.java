package com.forge.config;

import com.forge.auth.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {
    private final com.forge.infra.log.LogWebSocketHandler logWebSocketHandler;
    private final JwtService jwtService;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(logWebSocketHandler, "/ws/logs/**")
                .addInterceptors(new HandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(ServerHttpRequest req, ServerHttpResponse resp,
                                                   WebSocketHandler handler, Map<String, Object> attrs) {
                        if (req instanceof ServletServerHttpRequest servlet) {
                            String token = servlet.getServletRequest().getParameter("token");
                            return token != null && jwtService.validate(token).isPresent();
                        }
                        return false;
                    }
                    @Override
                    public void afterHandshake(ServerHttpRequest req, ServerHttpResponse resp,
                                               WebSocketHandler handler, Exception ex) {}
                })
                .setAllowedOrigins("*");
    }
}
