package com.forge.auth;

import com.forge.common.ApiException;
import com.forge.domain.ApiToken;
import com.forge.domain.ApiTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * API Token：脚本化调用 REST API 的长期凭证。
 * 明文 forge_ + 32 字节随机（Base64URL），库内只存 sha256；鉴权按哈希查唯一索引。
 */
@Service
@RequiredArgsConstructor
public class ApiTokenService {
    private final ApiTokenRepository repository;

    /** 创建令牌：返回完整明文（仅此一次，刷新页面后不可再取） */
    public ApiToken create(String name) {
        if (name == null || name.isBlank()) throw new ApiException("请填写令牌名称");
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String plain = "forge_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        ApiToken t = new ApiToken();
        t.setName(name.trim());
        t.setTokenHash(sha256(plain));
        t.setPrefix(plain.substring(0, 12));
        t = repository.save(t);
        // 明文放在 prefix 字段一次性带回给创建接口，前端展示后即丢弃
        t.setPrefix(plain);
        return t;
    }

    public List<ApiToken> list() {
        return repository.findAll();
    }

    @Transactional
    public void delete(String id) {
        if (!repository.existsById(id)) throw ApiException.notFound("令牌");
        repository.deleteById(id);
    }

    /** 校验明文令牌，命中则回填 principal（形如 token:ci，会出现在部署审计的操作人里） */
    public Optional<String> verify(String plain) {
        if (plain == null || plain.isBlank() || !plain.startsWith("forge_")) return Optional.empty();
        Optional<ApiToken> hit = repository.findByTokenHash(sha256(plain));
        if (hit.isEmpty()) return Optional.empty();
        ApiToken token = hit.get();
        // lastUsedAt 节流写库：一分钟内多次调用只落一次，避免高频 API 调用写放大
        if (token.getLastUsedAt() == null || token.getLastUsedAt().isBefore(LocalDateTime.now().minusMinutes(1))) {
            token.setLastUsedAt(LocalDateTime.now());
            repository.save(token);
        }
        return Optional.of("token:" + token.getName());
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
