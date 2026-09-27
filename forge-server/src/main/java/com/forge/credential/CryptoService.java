package com.forge.credential;

import com.forge.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

/**
 * AES-256-GCM 加密：凭据数据整体加密落库。
 * 密钥来源：环境变量 FORGE_SECRET_KEY（Base64 32 字节）或 {dataDir}/.secret.key（首次自动生成）。
 */
@Service
public class CryptoService {
    private static final String PREFIX = "enc:v1:";

    private final SecretKey key;

    public CryptoService(@Value("${forge.data-dir}") String dataDir) {
        try {
            Path keyFile = Path.of(dataDir, ".secret.key");
            String b64;
            String env = System.getenv("FORGE_SECRET_KEY");
            if (env != null && !env.isBlank()) {
                b64 = env;
            } else if (Files.exists(keyFile)) {
                b64 = Files.readString(keyFile).trim();
            } else {
                byte[] bytes = new byte[32];
                new SecureRandom().nextBytes(bytes);
                b64 = Base64.getEncoder().encodeToString(bytes);
                Files.createDirectories(keyFile.getParent());
                Files.writeString(keyFile, b64);
            }
            byte[] bytes = Base64.getDecoder().decode(b64);
            if (bytes.length != 32) throw new ApiException(500, "FORGE_SECRET_KEY 必须是 32 字节的 Base64");
            key = new SecretKeySpec(bytes, "AES");
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("初始化加密密钥失败", e);
        }
    }

    /** 加密 Map（JSON 序列化后整体加密） */
    public String encryptMap(Map<String, String> data) {
        return encryptText(com.forge.common.JsonUtils.toJson(data));
    }

    public Map<String, String> decryptMap(String blob) {
        return com.forge.common.JsonUtils.toStrMap(decryptText(blob));
    }

    public synchronized String encryptText(String plain) {
        try {
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(ct);
        } catch (Exception e) {
            throw new ApiException(500, "加密失败: " + e.getMessage());
        }
    }

    public synchronized String decryptText(String blob) {
        try {
            if (!blob.startsWith(PREFIX)) throw new ApiException(500, "密文格式错误");
            String[] parts = blob.substring(PREFIX.length()).split(":", 2);
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] ct = Base64.getDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ct), java.nio.charset.StandardCharsets.UTF_8);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(500, "解密失败（密钥不匹配？）: " + e.getMessage());
        }
    }
}
