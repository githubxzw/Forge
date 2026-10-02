package com.forge.infra.git;

import com.forge.domain.CredentialKind;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitAuthTest {

    @Test
    void sshKeyWritesTempFileAndEnv() throws Exception {
        try (GitAuth auth = GitAuth.of(CredentialKind.SSH_KEY,
                Map.of("privateKey", "-----BEGIN KEY-----\nabc\n-----END KEY-----"),
                "git@github.com:u/r.git")) {
            assertEquals(0, auth.args().size());
            String cmd = auth.env().get("GIT_SSH_COMMAND");
            assertTrue(cmd != null && cmd.contains("-i ") && cmd.contains("StrictHostKeyChecking=no"));
        }
    }

    @Test
    void passwordTokenGoesToCredentialFile() throws Exception {
        try (GitAuth auth = GitAuth.of(CredentialKind.PASSWORD,
                Map.of("username", "u", "password", "p@ss"),
                "https://github.com/u/r.git")) {
            assertTrue(auth.env().isEmpty());
            assertEquals(2, auth.args().size()); // -c credential.helper=store --file=...
            // secret 不允许出现在命令行参数里
            assertTrue(auth.args().stream().noneMatch(s -> s.contains("p@ss")));
        }
    }

    @Test
    void emptyDataFallsBackToNone() {
        GitAuth auth = GitAuth.of(CredentialKind.PASSWORD, Map.of(), "https://github.com/u/r.git");
        assertEquals(0, auth.args().size());
        assertTrue(auth.env().isEmpty());
    }
}
