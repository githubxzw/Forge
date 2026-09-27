package com.forge.credential;

import com.forge.common.ApiResult;
import com.forge.domain.CredentialKind;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/credentials")
@RequiredArgsConstructor
public class CredentialController {
    private final CredentialService credentialService;

    @GetMapping
    public ApiResult<List<Map<String, Object>>> list() {
        return ApiResult.ok(credentialService.list());
    }

    @PostMapping
    public ApiResult<Void> create(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        CredentialKind kind = CredentialKind.valueOf((String) body.get("kind"));
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) body.get("data");
        credentialService.create(name, kind, data);
        return ApiResult.ok();
    }

    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        CredentialKind kind = body.get("kind") == null ? null : CredentialKind.valueOf((String) body.get("kind"));
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) body.get("data");
        credentialService.update(id, name, kind, data);
        return ApiResult.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable String id) {
        credentialService.delete(id);
        return ApiResult.ok();
    }
}
