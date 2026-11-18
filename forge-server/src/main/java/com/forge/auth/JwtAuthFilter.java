package com.forge.auth;

import com.forge.domain.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.ZoneId;
import java.util.List;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ApiTokenService apiTokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                authenticateJwt(header.substring(7), request);
            } else if (header != null && header.startsWith("Token ")) {
                // API Token：脚本/CI 长期凭证（形如 forge_xxx），按 sha256 查库校验
                apiTokenService.verify(header.substring(6).trim()).ifPresent(principal ->
                        authenticate(principal, request));
            } else {
                String apiToken = request.getHeader("X-API-Token");
                if (apiToken != null && !apiToken.isBlank()) {
                    apiTokenService.verify(apiToken.trim()).ifPresent(principal ->
                            authenticate(principal, request));
                }
            }
        }
        chain.doFilter(request, response);
    }

    private void authenticateJwt(String token, HttpServletRequest request) {
        jwtService.parse(token).ifPresent(claims -> {
            String username = claims.getSubject();
            if (username == null) return;
            // 改密后旧 token 失效：签发时间早于 passwordChangedAt 的直接拒绝。
            // JWT 的 iat 只精确到秒，比较前把改密时间也截断到秒，避免同秒内签发的 token 被误杀
            var userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) return;
            Date issuedAt = claims.getIssuedAt();
            var changedAt = userOpt.get().getPasswordChangedAt();
            if (issuedAt != null && changedAt != null
                    && issuedAt.toInstant().isBefore(
                    changedAt.atZone(ZoneId.systemDefault()).toInstant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS))) {
                return;
            }
            // 角色以库内为准（升级期间的旧 token 无 role claim，改角色后无需等 token 过期）
            authenticate(username, userOpt.get().roleOrDefault().name(), request);
        });
    }

    private void authenticate(String principal, HttpServletRequest request) {
        authenticate(principal, "ADMIN", request);
    }

    private void authenticate(String principal, String role, HttpServletRequest request) {
        var auth = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role)));
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
