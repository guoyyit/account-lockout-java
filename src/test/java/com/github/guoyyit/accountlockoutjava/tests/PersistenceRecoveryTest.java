package com.github.guoyyit.accountlockoutjava.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.guoyyit.accountlockoutjava.audit.DefaultAuditLogger;
import com.github.guoyyit.accountlockoutjava.config.LockoutProperties;
import com.github.guoyyit.accountlockoutjava.domain.AttemptResult;
import com.github.guoyyit.accountlockoutjava.domain.ErrorCode;
import com.github.guoyyit.accountlockoutjava.domain.LockoutSnapshot;
import com.github.guoyyit.accountlockoutjava.service.DefaultLockoutEngine;
import com.github.guoyyit.accountlockoutjava.service.LockoutEngine;
import com.github.guoyyit.accountlockoutjava.store.FileStateStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceRecoveryTest {

    @TempDir
    Path tempDir;

    @Test
    void lockStateAndRemainingTimeSurviveRestart() {
        LockoutProperties properties = properties();
        MutableTimeSource clock = new MutableTimeSource(10_000L);

        FileStateStore firstStore = new FileStateStore(properties, objectMapper());
        firstStore.init();
        StubCredentialService credentials = new StubCredentialService().addAccount("alice");
        LockoutEngine firstEngine = new DefaultLockoutEngine(properties, firstStore, credentials,
                new DefaultAuditLogger(firstStore), clock);

        for (int i = 0; i < 3; i++) {
            firstEngine.attempt("alice", "wrong", "ip-1");
        }
        LockoutSnapshot before = firstEngine.snapshot("alice");
        assertTrue(before.isLocked());
        assertEquals(1, before.getLevel());

        clock.advanceMillis(Duration.ofSeconds(20).toMillis());

        FileStateStore restartedStore = new FileStateStore(properties, objectMapper());
        restartedStore.init();
        LockoutEngine restartedEngine = new DefaultLockoutEngine(properties, restartedStore, credentials,
                new DefaultAuditLogger(restartedStore), clock);

        LockoutSnapshot after = restartedEngine.snapshot("alice");
        assertTrue(after.isLocked());
        assertEquals(before.getLockedUntilEpochMilli(), after.getLockedUntilEpochMilli());
        assertEquals(before.getLockedUntilEpochMilli(), after.getLockedUntilEpochMilli());
        assertTrue(after.getRemainingMillis() <= before.getRemainingMillis());
        assertTrue(after.getRemainingMillis() >= Duration.ofSeconds(40).toMillis() - 5);

        AttemptResult correctCredentials = restartedEngine.attempt("alice", "good", "ip-2");
        assertEquals(ErrorCode.ACCOUNT_LOCKED, correctCredentials.errorCode());
    }

    @Test
    void restartAtLockBoundaryBehavesIdenticallyToNoRestart() {
        LockoutProperties properties = properties();
        MutableTimeSource clock = new MutableTimeSource(20_000L);

        FileStateStore store = new FileStateStore(properties, objectMapper());
        store.init();
        StubCredentialService credentials = new StubCredentialService().addAccount("alice");
        LockoutEngine engine = new DefaultLockoutEngine(properties, store, credentials,
                new DefaultAuditLogger(store), clock);

        engine.attempt("alice", "wrong", "ip-1");
        engine.attempt("alice", "wrong", "ip-1");

        FileStateStore restartedStore = new FileStateStore(properties, objectMapper());
        restartedStore.init();
        LockoutEngine restarted = new DefaultLockoutEngine(properties, restartedStore, credentials,
                new DefaultAuditLogger(restartedStore), clock);

        AttemptResult trigger = restarted.attempt("alice", "wrong", "ip-1");
        assertEquals(ErrorCode.ACCOUNT_LOCKED, trigger.errorCode());

        long lockedEvents = restartedStore.state().getEvents().stream()
                .filter(event -> "LOCKED".equals(event.type()))
                .count();
        assertEquals(1, lockedEvents);
    }

    @Test
    void auditTrailSurvivesRestartAndUnlockIsRecorded() {
        LockoutProperties properties = properties();
        MutableTimeSource clock = new MutableTimeSource(30_000L);

        FileStateStore store = new FileStateStore(properties, objectMapper());
        store.init();
        StubCredentialService credentials = new StubCredentialService().addAccount("alice");
        LockoutEngine engine = new DefaultLockoutEngine(properties, store, credentials,
                new DefaultAuditLogger(store), clock);

        for (int i = 0; i < 3; i++) {
            engine.attempt("alice", "wrong", "ip-1");
        }

        FileStateStore restartedStore = new FileStateStore(properties, objectMapper());
        restartedStore.init();
        DefaultAuditLogger restartedAudit = new DefaultAuditLogger(restartedStore);
        LockoutEngine restartedEngine = new DefaultLockoutEngine(properties, restartedStore, credentials,
                restartedAudit, clock);

        restartedEngine.unlock("alice", "operator-7");

        long unlocked = restartedAudit.query("alice", 100).stream()
                .filter(event -> "UNLOCKED".equals(event.type()))
                .count();
        assertEquals(1, unlocked);

        long failureEvents = restartedAudit.query("alice", 100).stream()
                .filter(event -> "FAILURE".equals(event.type()))
                .count();
        assertEquals(3, failureEvents);
    }

    private LockoutProperties properties() {
        LockoutProperties properties = new LockoutProperties();
        properties.setStorageDir(tempDir.toString());
        properties.setFailureWindow(Duration.ofMinutes(10));
        properties.setSourceThreshold(50);
        LockoutProperties.LockLevel level1 = new LockoutProperties.LockLevel();
        level1.setFailures(3);
        level1.setDuration(Duration.ofMinutes(1));
        LockoutProperties.LockLevel level2 = new LockoutProperties.LockLevel();
        level2.setFailures(5);
        level2.setDuration(Duration.ofMinutes(5));
        properties.setLevels(List.of(level1, level2));
        return properties;
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }
}
