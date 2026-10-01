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
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RestartRecoveryTest {

    @TempDir
    Path tempDir;

    @Test
    void failureCountsAndRemainingLockSurviveRestart() {
        LockoutProperties properties = properties(2);
        FixedClock clock = new FixedClock(Instant.ofEpochMilli(1_000_000));

        LockoutService before = newService(properties, clock);
        assertEquals(LoginOutcome.BAD_CREDENTIALS,
                before.attempt("alice", "bad", "1.1.1.1").outcome());
        assertEquals(LoginOutcome.BAD_CREDENTIALS,
                before.attempt("bob", "bad", "2.2.2.2").outcome());

        LockoutService afterOneFailure = newService(properties, clock);
        assertEquals(LoginOutcome.ACCOUNT_LOCKED,
                afterOneFailure.attempt("alice", "bad", "1.1.1.1").outcome(),
                "Lock threshold reached after restart uses the persisted counter");

        long retryBefore = afterOneFailure.attempt("alice", "pw", "1.1.1.1").retryAfterMillis();
        clock.advance(Duration.ofMillis(15_000));

        LockoutService afterRestart = newService(properties, clock);
        long retryAfter = afterRestart.attempt("alice", "pw", "1.1.1.1").retryAfterMillis();
        assertEquals(retryBefore - 15_000, retryAfter, 250,
                "Restart must not shorten the remaining lock time");
        assertEquals(LoginOutcome.ACCOUNT_LOCKED,
                afterRestart.attempt("alice", "pw", "1.1.1.1").outcome());
        assertEquals(LoginOutcome.ACCOUNT_LOCKED,
                afterRestart.attempt("bob", "bad", "2.2.2.2").outcome(),
                "Bob's persisted counter reaches his own lock after restart");

        clock.advance(Duration.ofMillis(46_000));
        LockoutService afterExpiry = newService(properties, clock);
        assertEquals(LoginOutcome.SUCCESS,
                afterExpiry.attempt("alice", "pw", "1.1.1.1").outcome(),
                "Lock expires at the originally scheduled absolute time");

        assertEquals(LoginOutcome.ACCOUNT_LOCKED,
                afterExpiry.attempt("bob", "pw", "2.2.2.2").outcome(),
                "Bob's lock persists with its own absolute expiry");
        assertEquals(1, afterExpiry.queryAudit("alice", AuditEventType.LOCK).size());
        assertEquals(2, afterExpiry.queryAudit("alice", AuditEventType.FAILURE).size());
    }

    @Test
    void restartAtLockCriticalPointBehavesLikeContinuousRun() {
        LockoutProperties continuousProps = properties(3);
        FixedClock continuousClock = new FixedClock(Instant.ofEpochMilli(2_000_000));
        LockoutService continuous = newService(continuousProps, continuousClock);
        for (int i = 0; i < 2; i++) {
            continuous.attempt("alice", "bad", "1.1.1.1");
        }
        LoginOutcome continuousTrigger =
                continuous.attempt("alice", "bad", "1.1.1.1").outcome();
        LoginOutcome continuousNext =
                continuous.attempt("alice", "pw", "1.1.1.1").outcome();

        LockoutProperties restartedProps = properties(3);
        FixedClock restartedClock = new FixedClock(Instant.ofEpochMilli(2_000_000));
        LockoutService first = newService(restartedProps, restartedClock);
        for (int i = 0; i < 2; i++) {
            first.attempt("alice", "bad", "1.1.1.1");
        }
        LockoutService restarted = newService(restartedProps, restartedClock);
        LoginOutcome restartedTrigger =
                restarted.attempt("alice", "bad", "1.1.1.1").outcome();
        LockoutService restartedAgain = newService(restartedProps, restartedClock);
        LoginOutcome restartedNext =
                restartedAgain.attempt("alice", "pw", "1.1.1.1").outcome();

        assertEquals(continuousTrigger, restartedTrigger);
        assertEquals(continuousNext, restartedNext);
        assertEquals(LoginOutcome.ACCOUNT_LOCKED, restartedNext);
    }

    private LockoutProperties properties(int threshold) {
        LockoutProperties properties = new LockoutProperties();
        properties.setStorageFile(tempDir.resolve("state-" + threshold + ".json").toString());
        properties.getAccount().setWindow(Duration.ofMinutes(10));
        properties.getAccount().setThreshold(threshold);
        properties.getAccount().setLevels(List.of(Duration.ofMinutes(1)));
        properties.getSource().setWindow(Duration.ofMinutes(10));
        properties.getSource().setThreshold(100);
        properties.setUsers(Map.of("alice", "pw", "bob", "pw"));
        properties.setAdminTokens(List.of("t"));
        return properties;
    }

    private LockoutService newService(LockoutProperties properties, Clock clock) {
        CredentialVerifier verifier = new PropertiesCredentialVerifier(properties);
        FileStateStore store = new FileStateStore(properties, new ObjectMapper());
        LockoutService service = new LockoutService(properties, verifier,
                new AdminAuthorizer(properties), store, clock);
        service.init();
        return service;
    }

    static class FixedClock extends Clock {
        private Instant instant;

        FixedClock(Instant instant) {
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
