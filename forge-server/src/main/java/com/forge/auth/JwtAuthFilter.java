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

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            jwtService.parse(header.substring(7)).ifPresent(claims -> {
                String username = claims.getSubject();
                if (username == null || SecurityContextHolder.getContext().getAuthentication() != null) return;
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
                var auth = new UsernamePasswordAuthenticationToken(username, null, List.of());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }
        chain.doFilter(request, response);
    }
}
