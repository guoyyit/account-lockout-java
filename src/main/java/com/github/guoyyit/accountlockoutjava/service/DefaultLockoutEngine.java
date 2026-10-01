package com.github.guoyyit.accountlockoutjava.service;

import com.github.guoyyit.accountlockoutjava.audit.AuditLogger;
import com.github.guoyyit.accountlockoutjava.config.LockoutProperties;
import com.github.guoyyit.accountlockoutjava.domain.AccountState;
import com.github.guoyyit.accountlockoutjava.domain.AttemptResult;
import com.github.guoyyit.accountlockoutjava.domain.ErrorCode;
import com.github.guoyyit.accountlockoutjava.domain.LockoutSnapshot;
import com.github.guoyyit.accountlockoutjava.domain.SourceState;
import com.github.guoyyit.accountlockoutjava.security.CredentialService;
import com.github.guoyyit.accountlockoutjava.store.LockoutState;
import com.github.guoyyit.accountlockoutjava.store.StateStore;
import org.springframework.stereotype.Service;

import java.util.Deque;
import java.util.List;

@Service
public class DefaultLockoutEngine implements LockoutEngine {

    private static final String EVENT_FAILURE = "FAILURE";
    private static final String EVENT_LOCKED = "LOCKED";
    private static final String EVENT_ESCALATED = "ESCALATED";
    private static final String EVENT_UNLOCKED = "UNLOCKED";

    private final LockoutProperties properties;
    private final StateStore stateStore;
    private final CredentialService credentialService;
    private final AuditLogger auditLogger;
    private final TimeSource timeSource;

    public DefaultLockoutEngine(LockoutProperties properties,
                                StateStore stateStore,
                                CredentialService credentialService,
                                AuditLogger auditLogger,
                                TimeSource timeSource) {
        this.properties = properties;
        this.stateStore = stateStore;
        this.credentialService = credentialService;
        this.auditLogger = auditLogger;
        this.timeSource = timeSource;
    }

    @Override
    public AttemptResult attempt(String username, String password, String source) {
        if (username == null || username.isBlank()) {
            return AttemptResult.failure(ErrorCode.INVALID_CREDENTIALS);
        }
        synchronized (stateStore) {
            long now = timeSource.nowEpochMilli();
            LockoutState state = stateStore.state();

            if (!credentialService.accountExists(username)) {
                return AttemptResult.failure(ErrorCode.UNKNOWN_ACCOUNT);
            }

            AccountState account = state.getAccounts().computeIfAbsent(username, key -> new AccountState());
            SourceState sourceState = sourceState(state, source);

            prune(account.getFailureTimes(), now);
            prune(sourceState.getFailureTimes(), now);

            boolean currentlyLocked = account.getLockUntilEpochMilli() > now;
            if (!currentlyLocked && account.getLockUntilEpochMilli() != 0) {
                account.getFailureTimes().clear();
                account.setLockLevel(0);
                account.setLockUntilEpochMilli(0L);
                account.setEscalationCount(0);
            }

            if (currentlyLocked) {
                return registerFailure(account, sourceState, username, source, now);
            }

            if (sourceBlocked(sourceState)) {
                return registerFailure(account, sourceState, username, source, now);
            }

            if (credentialService.credentialsMatch(username, password)) {
                account.getFailureTimes().clear();
                account.setEscalationCount(0);
                stateStore.persist();
                return AttemptResult.success();
            }

            return registerFailure(account, sourceState, username, source, now);
        }
    }

    @Override
    public void unlock(String username, String operator) {
        synchronized (stateStore) {
            LockoutState state = stateStore.state();
            AccountState account = state.getAccounts().get(username);
            if (account != null) {
                account.getFailureTimes().clear();
                account.setEscalationCount(0);
                account.setLockLevel(0);
                account.setLockUntilEpochMilli(0L);
            }
            auditLogger.record(EVENT_UNLOCKED, username, null, null,
                    "manual unlock by " + (operator != null ? operator : "unknown"));
            stateStore.persist();
        }
    }

    @Override
    public LockoutSnapshot snapshot(String username) {
        synchronized (stateStore) {
            long now = timeSource.nowEpochMilli();
            LockoutState state = stateStore.state();
            AccountState account = state.getAccounts().get(username);
            LockoutSnapshot snapshot = new LockoutSnapshot();
            if (account == null) {
                return snapshot;
            }
            prune(account.getFailureTimes(), now);
            boolean locked = account.getLockUntilEpochMilli() > now;
            snapshot.setLocked(locked);
            snapshot.setLevel(locked ? account.getLockLevel() : 0);
            snapshot.setLockedUntilEpochMilli(locked ? account.getLockUntilEpochMilli() : 0L);
            snapshot.setRemainingMillis(locked ? Math.max(0L, account.getLockUntilEpochMilli() - now) : 0L);
            snapshot.setFailuresInWindow(account.getFailureTimes().size());
            snapshot.setEscalationCount(account.getEscalationCount());
            stateStore.persist();
            return snapshot;
        }
    }

    private AttemptResult registerFailure(AccountState account, SourceState sourceState,
                                          String username, String source, long now) {
        account.getFailureTimes().addLast(now);
        account.setEscalationCount(account.getEscalationCount() + 1);
        if (sourceState != null) {
            sourceState.getFailureTimes().addLast(now);
        }

        int failuresInWindow = account.getFailureTimes().size();
        boolean alreadyLocked = account.getLockUntilEpochMilli() > now;
        int levelFailures = alreadyLocked ? account.getEscalationCount() : failuresInWindow;
        LockoutProperties.LockLevel targetLevel = levelFor(levelFailures);
        int targetLevelIndex = targetLevel == null ? 0 : levelIndex(targetLevel) + 1;
        if (targetLevel != null && (!alreadyLocked || targetLevelIndex > account.getLockLevel())) {
            long newLockUntil = now + targetLevel.getDuration().toMillis();
            long effectiveLockUntil = Math.max(account.getLockUntilEpochMilli(), newLockUntil);
            account.setLockUntilEpochMilli(effectiveLockUntil);
            account.setLockLevel(targetLevelIndex);
            if (alreadyLocked) {
                auditLogger.record(EVENT_ESCALATED, username, source, targetLevelIndex,
                        "escalated to level " + targetLevelIndex + " after "
                                + account.getEscalationCount() + " accumulated failures");
            } else {
                auditLogger.record(EVENT_LOCKED, username, source, targetLevelIndex,
                        "locked at level " + targetLevelIndex + " after "
                                + failuresInWindow + " failures in window");
            }
        }
        auditLogger.record(EVENT_FAILURE, username, source,
                account.getLockUntilEpochMilli() > now ? account.getLockLevel() : null,
                "bad credentials, failures=" + failuresInWindow);

        stateStore.persist();

        if (account.getLockUntilEpochMilli() > now) {
            return AttemptResult.failure(ErrorCode.ACCOUNT_LOCKED);
        }
        if (sourceBlocked(sourceState)) {
            return AttemptResult.failure(ErrorCode.SOURCE_BLOCKED);
        }
        return AttemptResult.failure(ErrorCode.INVALID_CREDENTIALS);
    }

    private SourceState sourceState(LockoutState state, String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        return state.getSources().computeIfAbsent(source, key -> new SourceState());
    }

    private boolean sourceBlocked(SourceState sourceState) {
        if (sourceState == null) {
            return false;
        }
        return sourceState.getFailureTimes().size() >= properties.getSourceThreshold();
    }

    private void prune(Deque<Long> failureTimes, long now) {
        long boundary = now - properties.getFailureWindow().toMillis();
        while (!failureTimes.isEmpty() && failureTimes.peekFirst() < boundary) {
            failureTimes.removeFirst();
        }
    }

    private LockoutProperties.LockLevel levelFor(int cumulativeFailures) {
        List<LockoutProperties.LockLevel> levels = properties.getLevels();
        LockoutProperties.LockLevel match = null;
        for (LockoutProperties.LockLevel level : levels) {
            if (cumulativeFailures >= level.getFailures()) {
                match = level;
            }
        }
        return match;
    }

    private int levelIndex(LockoutProperties.LockLevel target) {
        return properties.getLevels().indexOf(target);
    }

}
