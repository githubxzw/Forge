package com.forge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.auth.JwtAuthFilter;
import com.forge.common.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;
    private final ForgeProperties forgeProps;

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(forgeProps.getAllowedOrigins().split("\\s*,\\s*")));
        config.setAllowedMethods(List.of("*"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login", "/error", "/api/webhook/**").permitAll()
                // 日志下载改由控制器内 ticket/JWT 双通道鉴权（window.open 无法带 Authorization 头）
                .requestMatchers("/api/builds/*/log", "/api/deployments/*/log").permitAll()
                .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico", "/ws/**").permitAll()
                // SPA 路由刷新兜底：非 API/WS 的 GET 请求放行（真实静态资源或经 /error 转发到 index.html）
                .requestMatchers(req -> {
                    String uri = req.getRequestURI();
                    return "GET".equalsIgnoreCase(req.getMethod())
                            && !uri.startsWith("/api") && !uri.startsWith("/ws");
                }).permitAll()
                // ---- RBAC：ADMIN = 管理面（用户/令牌/设置/凭据/系统），OPERATOR = 日常操作，VIEWER = 只读 ----
                .requestMatchers("/api/auth/password").authenticated() // 改自己密码是自助操作，任何角色可用
                .requestMatchers("/api/users/**", "/api/tokens/**", "/api/settings/**",
                        "/api/system/**", "/api/credentials/**").hasRole("ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/projects/**",
                        "/api/environments/**", "/api/deployments/*/approve", "/api/deployments/*/reject").hasRole("ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/projects/**", "/api/environments/**").hasRole("ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/projects/**", "/api/environments/**").hasRole("ADMIN")
                // 其余 /api/**：写操作 OPERATOR+，读 VIEWER+
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/**").hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/**").hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/**").hasAnyRole("ADMIN", "OPERATOR")
                .anyRequest().authenticated())
            .exceptionHandling(eh -> eh.authenticationEntryPoint((req, resp, ex) -> {
                resp.setStatus(401);
                resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
                resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
                resp.getWriter().write(new ObjectMapper().writeValueAsString(ApiResult.error(401, "未登录或登录已过期")));
            }))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
