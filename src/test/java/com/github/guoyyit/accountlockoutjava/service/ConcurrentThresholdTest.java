package com.github.guoyyit.accountlockoutjava.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.accountlockoutjava.config.LockoutProperties;
import com.github.guoyyit.accountlockoutjava.model.AuditEventType;
import com.github.guoyyit.accountlockoutjava.model.LoginOutcome;
import com.github.guoyyit.accountlockoutjava.security.AdminAuthorizer;
import com.github.guoyyit.accountlockoutjava.security.CredentialVerifier;
import com.github.guoyyit.accountlockoutjava.security.PropertiesCredentialVerifier;
import com.github.guoyyit.accountlockoutjava.storage.FileStateStore;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConcurrentThresholdTest {

    @TempDir
    Path tempDir;

    @Test
    void concurrentFailuresAtThresholdTriggerExactlyOneLock() throws Exception {
        LockoutProperties properties = baseProperties();
        properties.getAccount().setThreshold(8);
        LockoutService service = newService(properties);

        int threads = 24;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        CountDownLatch done = new CountDownLatch(threads);
        List<Future<LoginOutcome>> futures = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    try {
                        return service.attempt("alice", "wrong", "1.1.1.1").outcome();
                    } finally {
                        done.countDown();
                    }
                }));
            }
            assertTrue(done.await(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        long badCredentials = futures.stream()
                .filter(f -> {
                    try {
                        return f.get() == LoginOutcome.BAD_CREDENTIALS;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .count();
        long accountLocked = futures.stream()
                .filter(f -> {
                    try {
                        return f.get() == LoginOutcome.ACCOUNT_LOCKED;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .count();

        assertEquals(7, badCredentials, "Exactly threshold-1 failures are accepted");
        assertEquals(17, accountLocked, "Exactly one attempt triggers the lock");
        assertEquals(1, service.queryAudit("alice", AuditEventType.LOCK).size());
        assertEquals(0, service.queryAudit("alice", AuditEventType.ESCALATION).size());
    }

    @Test
    void requestArrivingAfterLockDecisionObservesLockedState() throws Exception {
        LockoutProperties properties = baseProperties();
        properties.getAccount().setThreshold(2);
        LockoutService service = newService(properties);

        ExecutorService single = Executors.newSingleThreadExecutor();
        try {
            assertEquals(LoginOutcome.BAD_CREDENTIALS,
                    service.attempt("alice", "wrong", "1.1.1.1").outcome());
            Future<LoginOutcome> trigger = single.submit(
                    () -> service.attempt("alice", "wrong", "1.1.1.1").outcome());
            trigger.get(5, TimeUnit.SECONDS);
            Future<LoginOutcome> after1 = single.submit(
                    () -> service.attempt("alice", "wrong", "1.1.1.1").outcome());
            assertEquals(LoginOutcome.ACCOUNT_LOCKED, after1.get(5, TimeUnit.SECONDS));
            Future<LoginOutcome> after2 = single.submit(
                    () -> service.attempt("alice", "password123", "2.2.2.2").outcome());
            assertEquals(LoginOutcome.ACCOUNT_LOCKED, after2.get(5, TimeUnit.SECONDS));
        } finally {
            single.shutdownNow();
        }
    }

    @Test
    void concurrentSourceFailuresTriggerExactlyOneSourceBlock() throws Exception {
        LockoutProperties properties = baseProperties();
        properties.getAccount().setThreshold(100);
        properties.getSource().setThreshold(6);
        LockoutService service = newService(properties);

        int threads = 18;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        CountDownLatch done = new CountDownLatch(threads);
        List<Future<LoginOutcome>> futures = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    try {
                        return service.attempt("alice", "wrong", "9.9.9.9").outcome();
                    } finally {
                        done.countDown();
                    }
                }));
            }
            assertTrue(done.await(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        long blocked = futures.stream()
                .filter(f -> {
                    try {
                        return f.get() == LoginOutcome.SOURCE_BLOCKED;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .count();
        assertEquals(13, blocked);
    }

    private LockoutProperties baseProperties() {
        LockoutProperties properties = new LockoutProperties();
        properties.setStorageFile(tempDir.resolve("state-" + System.nanoTime() + ".json").toString());
        properties.getAccount().setWindow(Duration.ofMinutes(5));
        properties.getAccount().setThreshold(5);
        properties.getAccount().setLevels(
                List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30)));
        properties.getSource().setWindow(Duration.ofMinutes(5));
        properties.getSource().setThreshold(10);
        properties.setUsers(Map.of("alice", "password123"));
        properties.setAdminTokens(List.of("t"));
        return properties;
    }

    private LockoutService newService(LockoutProperties properties) {
        CredentialVerifier verifier = new PropertiesCredentialVerifier(properties);
        FileStateStore store = new FileStateStore(properties, new ObjectMapper());
        LockoutService service = new LockoutService(properties, verifier,
                new AdminAuthorizer(properties), store, Clock.systemUTC());
        service.init();
        return service;
    }
}
