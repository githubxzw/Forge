package com.forge.common;

import java.util.Base64;

/** FORGE_SECRET_KEY 统一解析：凭据加密（AES）与 JWT 签名共用同一把环境变量密钥，口径必须一致 */
public final class SecretKeys {

    private SecretKeys() {}

    /**
     * 解析 FORGE_SECRET_KEY：合法的 32 字节 Base64 返回原文字节；未设置返回 null。
     * 长度/格式不合法直接抛错——历史上 JwtService 接受任意长度而 CryptoService 要求 32 字节，
     * 传入非 32 字节时启动报错晦涩且两处行为不一致，此处统一为启动即失败、报错可读。
     */
    public static byte[] resolveEnvKey() {
        return resolve(System.getenv("FORGE_SECRET_KEY"));
    }

    /** 包内可见便于单测注入环境变量值 */
    static byte[] resolve(String env) {
        if (env == null || env.isBlank()) return null;
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(env.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("FORGE_SECRET_KEY 不是合法的 Base64（要求 32 字节，生成方式：openssl rand -base64 32）");
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("FORGE_SECRET_KEY 必须是 32 字节的 Base64（当前 " + bytes.length
                    + " 字节，生成方式：openssl rand -base64 32）");
        }
        return bytes;
    }
}
