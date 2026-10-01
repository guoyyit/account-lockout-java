package com.github.guoyyit.accountlockoutjava.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.accountlockoutjava.config.LockoutProperties;
import com.github.guoyyit.accountlockoutjava.model.AuditEventType;
import com.github.guoyyit.accountlockoutjava.model.LoginOutcome;
import com.github.guoyyit.accountlockoutjava.model.UnlockStatus;
import com.github.guoyyit.accountlockoutjava.security.AdminAuthorizer;
import com.github.guoyyit.accountlockoutjava.security.CredentialVerifier;
import com.github.guoyyit.accountlockoutjava.security.PropertiesCredentialVerifier;
import com.github.guoyyit.accountlockoutjava.storage.FileStateStore;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LockoutServiceBasicTest {

    @TempDir
    Path tempDir;

    private MutableClock clock;
    private LockoutService service;
    private AdminAuthorizer adminAuthorizer;

    @BeforeEach
    void setUp() {
        LockoutProperties properties = new LockoutProperties();
        properties.setStorageFile(tempDir.resolve("state.json").toString());
        properties.getAccount().setWindow(Duration.ofSeconds(10));
        properties.getAccount().setThreshold(3);
        properties.getAccount().setLevels(
                List.of(Duration.ofSeconds(30), Duration.ofSeconds(60), Duration.ofSeconds(120)));
        properties.getSource().setWindow(Duration.ofSeconds(10));
        properties.getSource().setThreshold(5);
        properties.setUsers(Map.of("alice", "password123"));
        properties.setAdminTokens(List.of("admin-token"));

        clock = new MutableClock(Instant.ofEpochMilli(1_000_000));
        CredentialVerifier verifier = new PropertiesCredentialVerifier(properties);
        adminAuthorizer = new AdminAuthorizer(properties);
        FileStateStore store = new FileStateStore(properties, new ObjectMapper());
        service = new LockoutService(properties, verifier, adminAuthorizer, store, clock);
        service.init();
    }

    @Test
    void successfulLoginReturnsSuccess() {
        assertEquals(LoginOutcome.SUCCESS,
                service.attempt("alice", "password123", "1.1.1.1").outcome());
    }

    @Test
    void thresholdFailuresLockAccountAndCorrectCredentialsRejectedWhileLocked() {
        for (int i = 0; i < 2; i++) {
            assertEquals(LoginOutcome.BAD_CREDENTIALS,
                    service.attempt("alice", "wrong", "1.1.1.1").outcome());
        }
        assertEquals(LoginOutcome.ACCOUNT_LOCKED,
                service.attempt("alice", "wrong", "1.1.1.1").outcome());

        assertEquals(LoginOutcome.ACCOUNT_LOCKED,
                service.attempt("alice", "password123", "2.2.2.2").outcome());
        assertTrue(service.attempt("alice", "password123", "2.2.2.2").retryAfterMillis() > 0);
    }

    @Test
    void successfulLoginResetsAccountFailuresBeforeThreshold() {
        assertEquals(LoginOutcome.BAD_CREDENTIALS,
                service.attempt("alice", "wrong", "1.1.1.1").outcome());
        assertEquals(LoginOutcome.SUCCESS,
                service.attempt("alice", "password123", "1.1.1.1").outcome());

        for (int i = 0; i < 2; i++) {
            assertEquals(LoginOutcome.BAD_CREDENTIALS,
                    service.attempt("alice", "wrong", "1.1.1.1").outcome());
        }
        assertEquals(LoginOutcome.ACCOUNT_LOCKED,
                service.attempt("alice", "wrong", "1.1.1.1").outcome());
    }

    @Test
    void unlockRequiresAdminPermissionAndClearsLock() {
        for (int i = 0; i < 3; i++) {
            service.attempt("alice", "wrong", "1.1.1.1");
        }
        assertEquals(LoginOutcome.ACCOUNT_LOCKED,
                service.attempt("alice", "password123", "1.1.1.1").outcome());

        assertEquals(UnlockStatus.UNAUTHORIZED, service.unlock("alice", "forged"));
        assertEquals(UnlockStatus.UNAUTHORIZED, service.unlock("alice", null));
        assertEquals(LoginOutcome.ACCOUNT_LOCKED,
                service.attempt("alice", "password123", "1.1.1.1").outcome());

        assertEquals(UnlockStatus.UNLOCKED, service.unlock("alice", "admin-token"));
        assertEquals(LoginOutcome.SUCCESS,
                service.attempt("alice", "password123", "1.1.1.1").outcome());

        assertEquals(UnlockStatus.NOT_LOCKED, service.unlock("alice", "admin-token"));
    }

    @Test
    void auditTrailContainsFailureLockAndUnlockEventsOnly() {
        service.attempt("alice", "wrong", "1.1.1.1");
        service.attempt("alice", "password123", "1.1.1.1");
        for (int i = 0; i < 3; i++) {
            service.attempt("alice", "wrong", "1.1.1.1");
        }
        service.unlock("alice", "admin-token");

        var events = service.queryAudit("alice", null);
        assertFalse(events.isEmpty());
        assertTrue(events.stream().anyMatch(e -> e.eventType() == AuditEventType.FAILURE));
        assertTrue(events.stream().anyMatch(e -> e.eventType() == AuditEventType.LOCK));
        assertTrue(events.stream().anyMatch(e -> e.eventType() == AuditEventType.UNLOCK));
        assertFalse(events.stream().anyMatch(e -> e.detail().contains("Authenticated")));

        assertEquals(1, service.queryAudit("alice", AuditEventType.LOCK).size());
        assertEquals(1, service.queryAudit("alice", AuditEventType.UNLOCK).size());
        assertEquals(4, service.queryAudit("alice", AuditEventType.FAILURE).size());
    }

    @Test
    void unauthorizedUnlockLeavesNoAuditRecord() {
        service.unlock("alice", "forged");
        assertTrue(service.queryAudit(null, AuditEventType.UNLOCK).isEmpty());
    }

    static class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
