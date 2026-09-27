package com.forge.auth;

import com.forge.config.ForgeProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expireMs;

    public JwtService(ForgeProperties props) {
        // 密钥来源：环境变量 FORGE_SECRET_KEY > {dataDir}/.jwt.secret（首次自动生成，重启不掉登录态）
        String secret = System.getenv("FORGE_SECRET_KEY");
        byte[] bytes;
        try {
            if (secret != null && !secret.isBlank()) {
                bytes = Base64.getDecoder().decode(secret);
            } else {
                java.nio.file.Path keyFile = props.dataPath().resolve(".jwt.secret");
                if (java.nio.file.Files.exists(keyFile)) {
                    bytes = Base64.getDecoder().decode(java.nio.file.Files.readString(keyFile).trim());
                } else {
                    bytes = new byte[48];
                    new SecureRandom().nextBytes(bytes);
                    java.nio.file.Files.writeString(keyFile, Base64.getEncoder().encodeToString(bytes));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("初始化 JWT 密钥失败", e);
        }
        this.key = Keys.hmacShaKeyFor(bytes.length >= 32 ? bytes : Base64.getEncoder().encode(bytes));
        this.expireMs = props.jwtExpire().toMillis();
    }

    public String issue(String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMs))
                .signWith(key)
                .compact();
    }

    public Optional<String> validate(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            return Optional.ofNullable(claims.getSubject());
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
