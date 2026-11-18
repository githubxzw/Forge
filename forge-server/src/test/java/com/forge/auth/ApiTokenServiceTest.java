package com.forge.auth;

import com.forge.common.ApiException;
import com.forge.domain.ApiToken;
import com.forge.domain.ApiTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** API Token：创建只存哈希、verify 按哈希命中、lastUsedAt 节流 */
class ApiTokenServiceTest {

    ApiTokenRepository repository;
    ApiTokenService service;
    final Map<String, ApiToken> table = new ConcurrentHashMap<>();

    @BeforeEach
    void setup() {
        repository = mock(ApiTokenRepository.class);
        service = new ApiTokenService(repository);
        table.clear();
        when(repository.save(any())).thenAnswer(inv -> {
            ApiToken t = inv.getArgument(0);
            if (t.getId() == null) t.setId("gen-1");
            table.put(t.getTokenHash(), t);
            return t;
        });
        when(repository.findByTokenHash(any())).thenAnswer(inv ->
                Optional.ofNullable(table.get((String) inv.getArgument(0))));
    }

    @Test
    void createStoresHashAndReturnsPlaintextOnce() {
        ApiToken created = service.create("ci-script");

        String plain = created.getPrefix(); // 明文经 prefix 字段一次性带回
        assertTrue(plain.startsWith("forge_"), plain);
        assertTrue(plain.length() >= 40, "应为 32 字节随机数的 Base64URL");

        ArgumentCaptor<ApiToken> cap = ArgumentCaptor.forClass(ApiToken.class);
        verify(repository, atLeast(1)).save(cap.capture());
        assertEquals(ApiTokenService.sha256(plain), cap.getValue().getTokenHash(), "库内只允许存 sha256");
        assertEquals("ci-script", cap.getValue().getName());
    }

    @Test
    void verifyMatchesByHashAndReturnsPrincipal() {
        ApiToken created = service.create("ci");
        String plain = created.getPrefix();

        Optional<String> principal = service.verify(plain);
        assertTrue(principal.isPresent());
        assertEquals("token:ci", principal.get(), "principal 形如 token:名称，进部署审计的操作人");

        assertTrue(service.verify("forge_notexist").isEmpty(), "未命中返回空");
        assertTrue(service.verify("garbage-no-prefix").isEmpty(), "非 forge_ 前缀直接拒绝");
    }

    @Test
    void verifyThrottlesLastUsedWrite() {
        ApiToken created = service.create("ci");
        ApiToken stored = table.values().iterator().next();
        LocalDateTime written = LocalDateTime.now().minusSeconds(5);
        stored.setLastUsedAt(written); // 1 分钟内已写过

        service.verify(created.getPrefix());
        assertEquals(written, stored.getLastUsedAt(), "节流窗口内不重复落库");
    }

    @Test
    void blankNameRejected() {
        assertThrows(ApiException.class, () -> service.create(" "));
    }
}
