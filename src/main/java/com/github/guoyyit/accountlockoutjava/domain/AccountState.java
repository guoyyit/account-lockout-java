package com.github.guoyyit.accountlockoutjava.domain;

import java.util.ArrayDeque;
import java.util.Deque;

public class AccountState {

    private Deque<Long> failureTimes = new ArrayDeque<>();
    private int escalationCount;
    private int lockLevel;
    private long lockUntilEpochMilli;

    public Deque<Long> getFailureTimes() {
        return failureTimes;
    }

    public void setFailureTimes(Deque<Long> failureTimes) {
        this.failureTimes = failureTimes;
    }

    public int getEscalationCount() {
        return escalationCount;
    }

    public void setEscalationCount(int escalationCount) {
        this.escalationCount = escalationCount;
    }

    public int getLockLevel() {
        return lockLevel;
    }

    public void setLockLevel(int lockLevel) {
        this.lockLevel = lockLevel;
    }

    public long getLockUntilEpochMilli() {
        return lockUntilEpochMilli;
    }

    public void setLockUntilEpochMilli(long lockUntilEpochMilli) {
        this.lockUntilEpochMilli = lockUntilEpochMilli;
    }
}
