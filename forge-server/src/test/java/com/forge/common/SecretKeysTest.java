package com.forge.common;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FORGE_SECRET_KEY 统一解析：32 字节 Base64 强约束（JWT 与凭据加密共用一把钥匙） */
class SecretKeysTest {

    private String b32() {
        byte[] bytes = new byte[32];
        for (int i = 0; i < 32; i++) bytes[i] = (byte) (i + 1);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    void unsetReturnsNull() {
        assertNull(SecretKeys.resolve(null));
        assertNull(SecretKeys.resolve(""));
        assertNull(SecretKeys.resolve("   "));
    }

    @Test
    void validKeyReturns32Bytes() {
        byte[] expected = new byte[32];
        for (int i = 0; i < 32; i++) expected[i] = (byte) (i + 1);
        assertArrayEquals(expected, SecretKeys.resolve(Base64.getEncoder().encodeToString(expected)));
        // 前后空白容错
        assertArrayEquals(expected, SecretKeys.resolve(" " + b32() + "\n"));
    }

    @Test
    void nonBase64FailsWithReadableMessage() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> SecretKeys.resolve("不是base64!!"));
        assertTrue(e.getMessage().contains("Base64"));
        assertTrue(e.getMessage().contains("openssl"));
    }

    @Test
    void wrongLengthFailsWithActualLength() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> SecretKeys.resolve(shortKey));
        assertTrue(e.getMessage().contains("16"));
        assertTrue(e.getMessage().contains("32"));
    }
}
