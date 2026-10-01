package com.github.guoyyit.accountlockoutjava.tests;

import com.github.guoyyit.accountlockoutjava.audit.DefaultAuditLogger;
import com.github.guoyyit.accountlockoutjava.config.LockoutProperties;
import com.github.guoyyit.accountlockoutjava.domain.AttemptResult;
import com.github.guoyyit.accountlockoutjava.domain.AuditEvent;
import com.github.guoyyit.accountlockoutjava.domain.ErrorCode;
import com.github.guoyyit.accountlockoutjava.service.DefaultLockoutEngine;
import com.github.guoyyit.accountlockoutjava.service.LockoutEngine;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockoutEngineConcurrencyTest {

    @Test
    void concurrentFailuresAtThresholdTriggerExactlyOneLock() throws Exception {
        LockoutProperties properties = new LockoutProperties();
        properties.setFailureWindow(Duration.ofMinutes(10));
        properties.setSourceThreshold(100);
        LockoutProperties.LockLevel level1 = new LockoutProperties.LockLevel();
        level1.setFailures(5);
        level1.setDuration(Duration.ofMinutes(5));
        properties.setLevels(List.of(level1));

        InMemoryTestStateStore store = new InMemoryTestStateStore();
        StubCredentialService credentials = new StubCredentialService().addAccount("alice");
        LockoutEngine engine = new DefaultLockoutEngine(properties, store, credentials,
                new DefaultAuditLogger(store), new MutableTimeSource(5_000L));

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                engine.attempt("alice", "wrong", "ip-" + Thread.currentThread().getId());
            });
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        long lockedEvents = store.state().getEvents().stream()
                .filter(event -> "LOCKED".equals(event.type()))
                .count();
        long escalatedEvents = store.state().getEvents().stream()
                .filter(event -> "ESCALATED".equals(event.type()))
                .count();
        assertEquals(1, lockedEvents, "exactly one LOCKED event at threshold");
        assertEquals(0, escalatedEvents);
        assertEquals(20, store.state().getAccounts().get("alice").getFailureTimes().size());

        AttemptResult lateArrival = engine.attempt("alice", "good", "ip-late");
        assertEquals(ErrorCode.ACCOUNT_LOCKED, lateArrival.errorCode());
    }

    @Test
    void concurrentAttemptsAreSerialisedAndNeverLosesCount() throws Exception {
        LockoutProperties properties = new LockoutProperties();
        properties.setFailureWindow(Duration.ofMinutes(10));
        properties.setSourceThreshold(100);
        LockoutProperties.LockLevel level1 = new LockoutProperties.LockLevel();
        level1.setFailures(50);
        level1.setDuration(Duration.ofMinutes(5));
        properties.setLevels(List.of(level1));

        InMemoryTestStateStore store = new InMemoryTestStateStore();
        StubCredentialService credentials = new StubCredentialService().addAccount("alice");
        LockoutEngine engine = new DefaultLockoutEngine(properties, store, credentials,
                new DefaultAuditLogger(store), new MutableTimeSource(5_000L));

        int threads = 32;
        int perThread = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                for (int j = 0; j < perThread; j++) {
                    engine.attempt("alice", "wrong", "ip-same");
                }
            });
        }

        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS));

        int recorded = store.state().getAccounts().get("alice").getFailureTimes().size();
        long failureEvents = store.state().getEvents().stream()
                .filter((AuditEvent event) -> "FAILURE".equals(event.type()))
                .count();
        assertEquals(threads * perThread, recorded);
        assertEquals(threads * perThread, failureEvents);
    }
}
