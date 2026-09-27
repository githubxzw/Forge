package com.forge.credential;

import com.forge.common.ApiException;
import com.forge.domain.Credential;
import com.forge.domain.CredentialKind;
import com.forge.domain.CredentialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CredentialService {
    private final CredentialRepository repository;
    private final CryptoService cryptoService;

    public List<Map<String, Object>> list() {
        return repository.findAll().stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("kind", c.getKind());
            m.put("createdAt", c.getCreatedAt());
            return m;
        }).toList();
    }

    public Credential create(String name, CredentialKind kind, Map<String, String> data) {
        if (repository.findByName(name).isPresent()) throw new ApiException("凭据名已存在: " + name);
        if (data == null || data.isEmpty()) throw new ApiException("凭据内容不能为空");
        Credential c = new Credential();
        c.setName(name);
        c.setKind(kind);
        c.setCipherText(cryptoService.encryptMap(data));
        return repository.save(c);
    }

    /** 更新：data 为空表示保留原密文 */
    public void update(String id, String name, CredentialKind kind, Map<String, String> data) {
        Credential c = repository.findById(id).orElseThrow(() -> ApiException.notFound("凭据"));
        if (name != null && !name.isBlank()) c.setName(name);
        if (kind != null) c.setKind(kind);
        if (data != null && !data.isEmpty()) c.setCipherText(cryptoService.encryptMap(data));
        repository.save(c);
    }

    public void delete(String id) {
        repository.deleteById(id);
    }

    /** 解密出凭据数据（仅服务端内部使用） */
    public Map<String, String> data(Credential c) {
        return cryptoService.decryptMap(c.getCipherText());
    }

    public Map<String, String> dataById(String id) {
        Credential c = repository.findById(id).orElseThrow(() -> new ApiException(400, "凭据不存在: " + id));
        return data(c);
    }

    public Credential require(String id) {
        return repository.findById(id).orElseThrow(() -> new ApiException(400, "凭据不存在: " + id));
    }
}
