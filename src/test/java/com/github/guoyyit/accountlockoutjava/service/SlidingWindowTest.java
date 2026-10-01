package com.github.guoyyit.accountlockoutjava.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.accountlockoutjava.config.LockoutProperties;
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

class SlidingWindowTest {

    @TempDir
    Path tempDir;

    private TestClock clock;
    private LockoutService service;
    private LockoutProperties properties;

    @BeforeEach
    void setUp() {
        properties = new LockoutProperties();
        properties.setStorageFile(tempDir.resolve("state.json").toString());
        properties.getAccount().setWindow(Duration.ofSeconds(10));
        properties.getAccount().setThreshold(3);
        properties.getAccount().setLevels(List.of(Duration.ofSeconds(30)));
        properties.getSource().setWindow(Duration.ofSeconds(10));
        properties.getSource().setThreshold(3);
        properties.setUsers(Map.of("alice", "pw", "bob", "pw"));
        properties.setAdminTokens(List.of("t"));

        clock = new TestClock(Instant.ofEpochMilli(100_000));
        CredentialVerifier verifier = new PropertiesCredentialVerifier(properties);
        FileStateStore store = new FileStateStore(properties, new ObjectMapper());
        service = new LockoutService(properties, verifier,
                new AdminAuthorizer(properties), store, clock);
        service.init();
    }

    @Test
    void failuresOutsideWindowExpireAndDoNotTriggerLock() {
        properties.getSource().setThreshold(100);
        assertEquals(LoginOutcome.BAD_CREDENTIALS, failAt(0));
        assertEquals(LoginOutcome.BAD_CREDENTIALS, failAt(1_000));
        clock.setInstant(Instant.ofEpochMilli(110_000));
        assertEquals(LoginOutcome.BAD_CREDENTIALS, failAt(0));
        assertEquals(LoginOutcome.BAD_CREDENTIALS, failAt(1_000));
        clock.setInstant(Instant.ofEpochMilli(120_000));
        assertEquals(LoginOutcome.BAD_CREDENTIALS, failAt(0));
        assertEquals(LoginOutcome.SUCCESS,
                service.attempt("alice", "pw", "1.1.1.1").outcome());
    }

    @Test
    void failureExactlyAtWindowBoundaryIsExcluded() {
        properties.getSource().setThreshold(100);
        assertEquals(LoginOutcome.BAD_CREDENTIALS, failAt(0));
        assertEquals(LoginOutcome.BAD_CREDENTIALS, failAt(1_000));

        clock.setInstant(Instant.ofEpochMilli(110_001));
        assertEquals(LoginOutcome.BAD_CREDENTIALS,
                service.attempt("alice", "bad", "1.1.1.1").outcome());
        clock.setInstant(Instant.ofEpochMilli(110_999));
        assertEquals(LoginOutcome.SUCCESS,
                service.attempt("alice", "pw", "1.1.1.1").outcome());
    }

    @Test
    void failureJustInsideWindowIsCountedAndTriggersLock() {
        properties.getSource().setThreshold(100);
        assertEquals(LoginOutcome.BAD_CREDENTIALS, failAt(0));
        assertEquals(LoginOutcome.BAD_CREDENTIALS, failAt(1_000));
        clock.setInstant(Instant.ofEpochMilli(109_999));
        assertEquals(LoginOutcome.ACCOUNT_LOCKED,
                service.attempt("alice", "bad", "1.1.1.1").outcome());
    }

    @Test
    void sourceWindowSlidesIndependentlyAndReleasesSource() {
        properties.getAccount().setThreshold(100);
        String source = "9.9.9.9";
        for (int i = 0; i < 2; i++) {
            assertEquals(LoginOutcome.BAD_CREDENTIALS,
                    service.attempt("bob", "bad", source).outcome());
        }
        assertEquals(LoginOutcome.SOURCE_BLOCKED,
                service.attempt("bob", "wrong-again", source).outcome());

        clock.setInstant(Instant.ofEpochMilli(110_001));
        assertEquals(LoginOutcome.BAD_CREDENTIALS,
                service.attempt("bob", "bad", source).outcome());
        assertEquals(LoginOutcome.BAD_CREDENTIALS,
                service.attempt("bob", "bad", source).outcome());
        assertEquals(LoginOutcome.SOURCE_BLOCKED,
                service.attempt("bob", "bad", source).outcome());
    }

    private LoginOutcome failAt(long millis) {
        clock.advance(Duration.ofMillis(millis));
        return service.attempt("alice", "bad", "1.1.1.1").outcome();
    }

    static class TestClock extends Clock {
        private Instant instant;

        TestClock(Instant instant) {
            this.instant = instant;
        }

        void setInstant(Instant instant) {
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
