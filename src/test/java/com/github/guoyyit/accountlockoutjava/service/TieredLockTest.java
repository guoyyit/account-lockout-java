package com.github.guoyyit.accountlockoutjava.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.accountlockoutjava.config.LockoutProperties;
import com.github.guoyyit.accountlockoutjava.model.AuditEventType;
import com.github.guoyyit.accountlockoutjava.model.AuditRecord;
import com.github.guoyyit.accountlockoutjava.model.LoginOutcome;
import com.github.guoyyit.accountlockoutjava.security.AdminAuthorizer;
import com.github.guoyyit.accountlockoutjava.security.CredentialVerifier;
import com.github.guoyyit.accountlockoutjava.security.PropertiesCredentialVerifier;
import com.github.guoyyit.accountlockoutjava.storage.FileStateStore;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TieredLockTest {

    @TempDir
    Path tempDir;

    private StepClock clock;
    private LockoutService service;

    @BeforeEach
    void setUp() {
        LockoutProperties properties = new LockoutProperties();
        properties.setStorageFile(tempDir.resolve("state.json").toString());
        properties.getAccount().setWindow(Duration.ofSeconds(100));
        properties.getAccount().setThreshold(2);
        properties.getAccount().setLevels(
                List.of(Duration.ofSeconds(10), Duration.ofSeconds(60), Duration.ofSeconds(300)));
        properties.getSource().setWindow(Duration.ofSeconds(100));
        properties.getSource().setThreshold(100);
        properties.setUsers(Map.of("alice", "pw"));
        properties.setAdminTokens(List.of("t"));

        clock = new StepClock(Instant.ofEpochMilli(1_000_000));
        CredentialVerifier verifier = new PropertiesCredentialVerifier(properties);
        FileStateStore store = new FileStateStore(properties, new ObjectMapper());
        service = new LockoutService(properties, verifier,
                new AdminAuthorizer(properties), store, clock);
        service.init();
    }

    @Test
    void locksEscalateToHigherLevelsWithLongerDurationsAfterExpiry() {
        triggerLock();
        long firstRetry = service.attempt("alice", "pw", "s").retryAfterMillis();
        assertTrue(firstRetry <= 10_000);
        assertLastLockEvent(AuditEventType.LOCK, 1);

        clock.advance(Duration.ofSeconds(10));
        triggerLock();
        long secondRetry = service.attempt("alice", "pw", "s").retryAfterMillis();
        assertTrue(secondRetry > 10_000 && secondRetry <= 60_000);
        assertLastLockEvent(AuditEventType.ESCALATION, 2);

        clock.advance(Duration.ofSeconds(60));
        triggerLock();
        long thirdRetry = service.attempt("alice", "pw", "s").retryAfterMillis();
        assertTrue(thirdRetry > 60_000 && thirdRetry <= 300_000);
        assertLastLockEvent(AuditEventType.ESCALATION, 3);

        clock.advance(Duration.ofSeconds(300));
        triggerLock();
        long cappedRetry = service.attempt("alice", "pw", "s").retryAfterMillis();
        assertTrue(cappedRetry > 60_000 && cappedRetry <= 300_000,
                "Level is capped at the highest configured level");
        assertLastLockEvent(AuditEventType.ESCALATION, 3);

        clock.advance(Duration.ofSeconds(300));
        assertEquals(com.github.guoyyit.accountlockoutjava.model.UnlockStatus.UNLOCKED,
                service.unlock("alice", "t"));
        triggerLock();
        long restartedRetry = service.attempt("alice", "pw", "s").retryAfterMillis();
        assertTrue(restartedRetry <= 10_000,
                "Manual unlock resets the level chain back to lowest");
        assertLastLockEvent(AuditEventType.LOCK, 1);
    }

    @Test
    void escalationNeverShortensRemainingLockTime() {
        triggerLock();
        long lockedUntilFirst = service.attempt("alice", "pw", "s").retryAfterMillis();

        clock.advance(Duration.ofSeconds(5));
        long stillLocked = service.attempt("alice", "pw", "s").retryAfterMillis();
        assertEquals(lockedUntilFirst - 5_000, stillLocked, 50,
                "Attempts during lock do not change the remaining lock time");

        clock.advance(Duration.ofSeconds(6));
        triggerLock();
        long secondRetry = service.attempt("alice", "pw", "s").retryAfterMillis();
        assertTrue(secondRetry > 10_000);
    }

    private void triggerLock() {
        assertEquals(LoginOutcome.BAD_CREDENTIALS,
                service.attempt("alice", "bad", "s").outcome());
        assertEquals(LoginOutcome.ACCOUNT_LOCKED,
                service.attempt("alice", "bad", "s").outcome());
    }

    private void assertLastLockEvent(AuditEventType expectedType, int expectedLevel) {
        List<AuditRecord> locks = service.queryAudit("alice", expectedType);
        AuditRecord last = locks.get(locks.size() - 1);
        assertEquals(expectedType, last.eventType());
        assertTrue(last.detail().contains("level " + expectedLevel), last.detail());
    }

    static class StepClock extends Clock {
        private Instant instant;

        StepClock(Instant instant) {
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
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
