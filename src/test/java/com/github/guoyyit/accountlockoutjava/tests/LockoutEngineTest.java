package com.github.guoyyit.accountlockoutjava.tests;

import com.github.guoyyit.accountlockoutjava.audit.DefaultAuditLogger;
import com.github.guoyyit.accountlockoutjava.config.LockoutProperties;
import com.github.guoyyit.accountlockoutjava.domain.AttemptResult;
import com.github.guoyyit.accountlockoutjava.domain.ErrorCode;
import com.github.guoyyit.accountlockoutjava.service.DefaultLockoutEngine;
import com.github.guoyyit.accountlockoutjava.service.LockoutEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockoutEngineTest {

    private static final long START = 1_000_000L;

    private LockoutProperties properties;
    private MutableTimeSource clock;
    private InMemoryTestStateStore store;
    private LockoutEngine engine;

    @BeforeEach
    void setUp() {
        properties = new LockoutProperties();
        properties.setFailureWindow(Duration.ofMinutes(10));
        properties.setSourceThreshold(4);
        properties.setLevels(java.util.List.of(
                level(3, Duration.ofMinutes(1)),
                level(5, Duration.ofMinutes(5)),
                level(8, Duration.ofMinutes(30))));
        clock = new MutableTimeSource(START);
        store = new InMemoryTestStateStore();
        StubCredentialService credentials = new StubCredentialService().addAccount("alice").addAccount("bob");
        engine = new DefaultLockoutEngine(properties, store, credentials,
                new DefaultAuditLogger(store), clock);
    }

    @Test
    void locksAfterThresholdAndRejectsCorrectCredentials() {
        for (int i = 0; i < 2; i++) {
            assertEquals(ErrorCode.INVALID_CREDENTIALS, fail("alice", "ip-1").errorCode());
        }
        AttemptResult third = fail("alice", "ip-1");
        assertEquals(ErrorCode.ACCOUNT_LOCKED, third.errorCode());

        AttemptResult duringLock = engine.attempt("alice", "good", "ip-1");
        assertEquals(ErrorCode.ACCOUNT_LOCKED, duringLock.errorCode());
        assertTrue(engine.snapshot("alice").isLocked());

        clock.advanceMillis(Duration.ofMinutes(1).toMillis() + 1);
        AttemptResult afterExpiry = engine.attempt("alice", "good", "ip-9");
        assertEquals(AttemptResult.Outcome.SUCCESS, afterExpiry.outcome());
        assertFalse(engine.snapshot("alice").isLocked());
    }

    @Test
    void slidingWindowExpiresOldFailures() {
        fail("alice", "ip-1");
        fail("alice", "ip-1");
        clock.advanceMillis(Duration.ofMinutes(10).toMillis() + 1);
        fail("alice", "ip-1");

        assertEquals(ErrorCode.INVALID_CREDENTIALS, fail("alice", "ip-1").errorCode());
        assertFalse(engine.snapshot("alice").isLocked());
    }

    @Test
    void windowBoundaryIsExclusive() {
        fail("alice", "ip-1");
        fail("alice", "ip-1");
        clock.advanceMillis(Duration.ofMinutes(10).toMillis());

        AttemptResult atBoundary = fail("alice", "ip-1");
        assertEquals(ErrorCode.ACCOUNT_LOCKED, atBoundary.errorCode());
    }

    @Test
    void escalatesLevelWithoutShorteningRemainingTime() {
        failTimes("alice", "ip-1", 3);
        assertEquals(1, engine.snapshot("alice").getLevel());

        clock.advanceMillis(Duration.ofSeconds(50).toMillis());
        failTimes("alice", "ip-1", 2);
        assertEquals(2, engine.snapshot("alice").getLevel());

        long remaining = engine.snapshot("alice").getRemainingMillis();
        assertTrue(remaining > Duration.ofMinutes(4).toMillis());
        assertTrue(remaining <= Duration.ofMinutes(5).toMillis());
    }

    @Test
    void afterLockExpiryNextFailureStartsAtLowestLevel() {
        failTimes("alice", "ip-1", 5);
        assertEquals(2, engine.snapshot("alice").getLevel());

        clock.advanceMillis(Duration.ofMinutes(6).toMillis());
        AttemptResult first = fail("alice", "ip-fresh");
        assertEquals(ErrorCode.INVALID_CREDENTIALS, first.errorCode());
        assertEquals(0, engine.snapshot("alice").getLevel());

        failTimes("alice", "ip-fresh", 2);
        assertEquals(1, engine.snapshot("alice").getLevel());
    }

    @Test
    void sourceBlockingOnlyRejectsThatSource() {
        properties.setSourceThreshold(2);
        properties.setLevels(java.util.List.of(
                level(5, Duration.ofMinutes(1)),
                level(8, Duration.ofMinutes(5))));
        failTimes("bob", "bad-ip", 2);
        AttemptResult blocked = engine.attempt("bob", "good", "bad-ip");
        assertEquals(ErrorCode.SOURCE_BLOCKED, blocked.errorCode());

        AttemptResult otherSource = engine.attempt("bob", "good", "good-ip");
        assertEquals(AttemptResult.Outcome.SUCCESS, otherSource.outcome());
    }

    @Test
    void sourceBlockingOnlyRejectsThatSourceForAlice() {
        properties.setSourceThreshold(2);
        properties.setLevels(java.util.List.of(
                level(5, Duration.ofMinutes(1)),
                level(8, Duration.ofMinutes(5))));
        failTimes("alice", "bad-ip", 2);
        AttemptResult blocked = engine.attempt("alice", "good", "bad-ip");
        assertEquals(ErrorCode.SOURCE_BLOCKED, blocked.errorCode());

        AttemptResult otherSource = engine.attempt("alice", "good", "good-ip");
        assertEquals(AttemptResult.Outcome.SUCCESS, otherSource.outcome());
    }

    @Test
    void unknownAccountIsIndistinguishableAndNotCounted() {
        AttemptResult missing = engine.attempt("ghost", "whatever", "ip-1");
        assertEquals(AttemptResult.Outcome.FAILURE, missing.outcome());
        assertTrue(store.state().getAccounts().isEmpty());

        for (int i = 0; i < 10; i++) {
            engine.attempt("ghost", "x", "ip-1");
        }
        assertTrue(store.state().getAccounts().isEmpty());
    }

    @Test
    void unlockClearsLockAndFailureState() {
        failTimes("alice", "ip-1", 3);
        assertTrue(engine.snapshot("alice").isLocked());

        engine.unlock("alice", "operator-1");
        assertFalse(engine.snapshot("alice").isLocked());

        AttemptResult result = engine.attempt("alice", "good", "ip-1");
        assertEquals(AttemptResult.Outcome.SUCCESS, result.outcome());
    }

    @Test
    void successfulLoginResetsFailuresButNotSourceCounters() {
        failTimes("alice", "ip-1", 2);
        AttemptResult success = engine.attempt("alice", "good", "ip-1");
        assertEquals(AttemptResult.Outcome.SUCCESS, success.outcome());
        assertEquals(0, engine.snapshot("alice").getFailuresInWindow());
    }

    private AttemptResult fail(String username, String source) {
        return engine.attempt(username, "wrong", source);
    }

    private void failTimes(String username, String source, int times) {
        for (int i = 0; i < times; i++) {
            fail(username, source);
        }
    }

    private static LockoutProperties.LockLevel level(int failures, Duration duration) {
        LockoutProperties.LockLevel lockLevel = new LockoutProperties.LockLevel();
        lockLevel.setFailures(failures);
        lockLevel.setDuration(duration);
        return lockLevel;
    }
}
