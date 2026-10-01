package com.github.guoyyit.accountlockoutjava.service;

import com.github.guoyyit.accountlockoutjava.config.LockoutProperties;
import com.github.guoyyit.accountlockoutjava.model.AuditEventType;
import com.github.guoyyit.accountlockoutjava.model.AuditRecord;
import com.github.guoyyit.accountlockoutjava.model.AccountState;
import com.github.guoyyit.accountlockoutjava.model.LoginOutcome;
import com.github.guoyyit.accountlockoutjava.model.LoginResult;
import com.github.guoyyit.accountlockoutjava.model.LockoutState;
import com.github.guoyyit.accountlockoutjava.model.SourceState;
import com.github.guoyyit.accountlockoutjava.model.UnlockStatus;
import com.github.guoyyit.accountlockoutjava.security.AdminAuthorizer;
import com.github.guoyyit.accountlockoutjava.security.CredentialVerifier;
import com.github.guoyyit.accountlockoutjava.storage.FileStateStore;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;

@Service
public class LockoutService {

    private final LockoutProperties properties;
    private final CredentialVerifier credentialVerifier;
    private final AdminAuthorizer adminAuthorizer;
    private final FileStateStore stateStore;
    private final Clock clock;

    private final ReentrantLock guard = new ReentrantLock();
    private LockoutState state;

    public LockoutService(LockoutProperties properties,
                          CredentialVerifier credentialVerifier,
                          AdminAuthorizer adminAuthorizer,
                          FileStateStore stateStore,
                          Clock clock) {
        this.properties = properties;
        this.credentialVerifier = credentialVerifier;
        this.adminAuthorizer = adminAuthorizer;
        this.stateStore = stateStore;
        this.clock = clock;
    }

    @PostConstruct
    void init() {
        this.state = stateStore.load();
    }

    public LoginResult attempt(String username, String password, String source) {
        String sourceKey = (source == null || source.isBlank()) ? "unknown" : source;
        guard.lock();
        try {
            long now = clock.millis();

            AccountState accountState = state.getAccounts().get(username);
            if (accountState != null && accountState.getLockedUntil() > now) {
                return new LoginResult(LoginOutcome.ACCOUNT_LOCKED,
                        "Account is locked", accountState.getLockedUntil() - now);
            }

            prune(accountState == null ? null : accountState.getFailureTimes(),
                    properties.getAccount().getWindow(), now);
            SourceState sourceState = state.getSources().get(sourceKey);
            prune(sourceState == null ? null : sourceState.getFailureTimes(),
                    properties.getSource().getWindow(), now);
            if (sourceState != null
                    && sourceState.getFailureTimes().size() >= properties.getSource().getThreshold()) {
                int lastIndex = sourceState.getFailureTimes().size() - 1;
                long newest = sourceState.getFailureTimes().get(lastIndex);
                long retryAfter = newest + properties.getSource().getWindow().toMillis() - now;
                return new LoginResult(LoginOutcome.SOURCE_BLOCKED,
                        "Source is temporarily blocked", Math.max(0, retryAfter));
            }

            CredentialVerifier.Status status = credentialVerifier.verify(username, password);
            if (status == CredentialVerifier.Status.NOT_FOUND) {
                return new LoginResult(LoginOutcome.BAD_CREDENTIALS,
                        "Invalid username or password", 0);
            }

            if (status == CredentialVerifier.Status.EXISTS_MATCH) {
                if (accountState != null && !accountState.getFailureTimes().isEmpty()) {
                    accountState.getFailureTimes().clear();
                    persist();
                }
                return new LoginResult(LoginOutcome.SUCCESS, "Authenticated", 0);
            }

            return registerFailure(username, sourceKey, now,
                    accountState == null ? new AccountState() : accountState,
                    sourceState == null ? new SourceState() : sourceState);
        } finally {
            guard.unlock();
        }
    }

    public UnlockStatus unlock(String account, String operatorToken) {
        if (!adminAuthorizer.isAuthorized(operatorToken)) {
            return UnlockStatus.UNAUTHORIZED;
        }
        guard.lock();
        try {
            AccountState accountState = state.getAccounts().get(account);
            long now = clock.millis();
            boolean locked = accountState != null
                    && (accountState.getLockedUntil() > now || accountState.getLockLevel() > 0);
            if (!locked) {
                return UnlockStatus.NOT_LOCKED;
            }
            accountState.getFailureTimes().clear();
            accountState.setLockLevel(0);
            accountState.setLockedUntil(0);
            recordAudit(AuditEventType.UNLOCK, account, null, operatorToken,
                    "Manual unlock by administrator");
            persist();
            return UnlockStatus.UNLOCKED;
        } finally {
            guard.unlock();
        }
    }

    public List<AuditRecord> queryAudit(String account, AuditEventType eventType) {
        guard.lock();
        try {
            List<AuditRecord> matches = new ArrayList<>();
            for (AuditRecord record : state.getAudit()) {
                if (account != null && !account.equals(record.account())) {
                    continue;
                }
                if (eventType != null && eventType != record.eventType()) {
                    continue;
                }
                matches.add(record);
            }
            return List.copyOf(matches);
        } finally {
            guard.unlock();
        }
    }

    private LoginResult registerFailure(String username, String sourceKey, long now,
                                        AccountState accountState, SourceState sourceState) {
        if (!state.getAccounts().containsKey(username)) {
            state.getAccounts().put(username, accountState);
        }
        if (!state.getSources().containsKey(sourceKey)) {
            state.getSources().put(sourceKey, sourceState);
        }

        accountState.getFailureTimes().add(now);
        sourceState.getFailureTimes().add(now);

        recordAudit(AuditEventType.FAILURE, username, sourceKey, null,
                "Credential verification failed");

        LoginResult result = new LoginResult(LoginOutcome.BAD_CREDENTIALS,
                "Invalid username or password", 0);
        if (accountState.getFailureTimes().size() >= properties.getAccount().getThreshold()) {
            int levels = properties.getAccount().getLevels().size();
            int nextLevel = Math.min(Math.max(accountState.getLockLevel() + 1, 1), levels);
            Duration lockDuration = properties.getAccount().getLevels().get(nextLevel - 1);
            long newLockedUntil = now + lockDuration.toMillis();
            accountState.setLockedUntil(Math.max(accountState.getLockedUntil(), newLockedUntil));
            accountState.setLockLevel(nextLevel);
            accountState.getFailureTimes().clear();
            AuditEventType lockEvent =
                    nextLevel > 1 ? AuditEventType.ESCALATION : AuditEventType.LOCK;
            recordAudit(lockEvent, username, sourceKey, null,
                    "Account locked at level " + nextLevel + " for " + lockDuration);
            result = new LoginResult(LoginOutcome.ACCOUNT_LOCKED,
                    "Account is locked", accountState.getLockedUntil() - now);
        } else if (sourceState.getFailureTimes().size() >= properties.getSource().getThreshold()) {
            int lastIndex = sourceState.getFailureTimes().size() - 1;
            long newest = sourceState.getFailureTimes().get(lastIndex);
            long retryAfter = newest + properties.getSource().getWindow().toMillis() - now;
            result = new LoginResult(LoginOutcome.SOURCE_BLOCKED,
                    "Source is temporarily blocked", Math.max(0, retryAfter));
        }

        persist();
        return result;
    }

    private void prune(List<Long> failureTimes, Duration window, long now) {
        if (failureTimes == null || failureTimes.isEmpty()) {
            return;
        }
        long cutoff = now - window.toMillis();
        Iterator<Long> iterator = failureTimes.iterator();
        while (iterator.hasNext()) {
            if (iterator.next() <= cutoff) {
                iterator.remove();
            } else {
                break;
            }
        }
    }

    private void recordAudit(AuditEventType eventType, String account, String source,
                             String operator, String detail) {
        String id = "evt-" + (state.getAudit().size() + 1) + "-"
                + UUID.randomUUID().toString().substring(0, 8);
        state.getAudit().add(new AuditRecord(id, clock.millis(), eventType, account, source,
                operator, detail));
    }

    private void persist() {
        stateStore.save(state);
    }
}
