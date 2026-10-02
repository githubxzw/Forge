package com.forge.credential;

import com.forge.common.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CryptoServiceTest {

    @TempDir
    Path dir;

    @Test
    void mapRoundtrip() {
        CryptoService svc = new CryptoService(dir.toString());
        String blob = svc.encryptMap(Map.of("password", "s3cret", "key", "v&lue=+中文"));
        assertTrue(blob.startsWith("enc:v1:"));
        Map<String, String> out = svc.decryptMap(blob);
        assertEquals("s3cret", out.get("password"));
        assertEquals("v&lue=+中文", out.get("key"));
    }

    @Test
    void cipherDiffersFromPlain() {
        CryptoService svc = new CryptoService(dir.toString());
        String blob = svc.encryptText("admin123");
        assertNotEquals("admin123", blob);
    }

    @Test
    void wrongKeyFailsToDecrypt() {
        CryptoService svc1 = new CryptoService(dir.resolve("a").toString());
        String blob = svc1.encryptText("secret");
        CryptoService svc2 = new CryptoService(dir.resolve("b").toString()); // 不同密钥
        assertThrows(ApiException.class, () -> svc2.decryptText(blob));
    }
}
