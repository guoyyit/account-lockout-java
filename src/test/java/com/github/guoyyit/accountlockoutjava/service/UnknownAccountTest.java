package com.github.guoyyit.accountlockoutjava.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.accountlockoutjava.config.LockoutProperties;
import com.github.guoyyit.accountlockoutjava.model.LoginOutcome;
import com.github.guoyyit.accountlockoutjava.model.LoginResult;
import com.github.guoyyit.accountlockoutjava.security.AdminAuthorizer;
import com.github.guoyyit.accountlockoutjava.security.CredentialVerifier;
import com.github.guoyyit.accountlockoutjava.security.PropertiesCredentialVerifier;
import com.github.guoyyit.accountlockoutjava.storage.FileStateStore;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UnknownAccountTest {

    @TempDir
    Path tempDir;

    private LockoutService service;

    @BeforeEach
    void setUp() {
        LockoutProperties properties = new LockoutProperties();
        properties.setStorageFile(tempDir.resolve("state.json").toString());
        properties.getAccount().setWindow(Duration.ofMinutes(5));
        properties.getAccount().setThreshold(2);
        properties.getAccount().setLevels(List.of(Duration.ofMinutes(1)));
        properties.getSource().setWindow(Duration.ofMinutes(5));
        properties.getSource().setThreshold(2);
        properties.setUsers(Map.of("alice", "pw"));
        properties.setAdminTokens(List.of("t"));

        CredentialVerifier verifier = new PropertiesCredentialVerifier(properties);
        FileStateStore store = new FileStateStore(properties, new ObjectMapper());
        service = new LockoutService(properties, verifier,
                new AdminAuthorizer(properties), store, Clock.systemUTC());
        service.init();
    }

    @Test
    void unknownAccountIsIndistinguishableFromWrongPasswordAndNeverCounts() {
        LoginResult wrongPassword = service.attempt("alice", "wrong", "1.1.1.1");
        for (int i = 0; i < 20; i++) {
            LoginResult ghost = service.attempt("ghost", "anything", "1.1.1.1");
            assertEquals(wrongPassword.outcome(), ghost.outcome());
            assertEquals(wrongPassword.message(), ghost.message());
            assertEquals(0, ghost.retryAfterMillis());
        }

        assertEquals(LoginOutcome.ACCOUNT_LOCKED,
                service.attempt("alice", "wrong", "1.1.1.1").outcome(),
                "Alice reaches her own lock, while the source was never blocked by ghost attempts");

        assertEquals(0, service.queryAudit("ghost", null).size(),
                "No failure audit is recorded for unknown accounts");
        assertEquals(0, service.queryAudit("ghost", null).size());
    }

    @Test
    void unknownAccountNeverLocksEvenWithRepeatedAttempts() {
        for (int i = 0; i < 20; i++) {
            assertEquals(LoginOutcome.BAD_CREDENTIALS,
                    service.attempt("nobody", "x", "2.2.2.2").outcome());
        }
    }
}
