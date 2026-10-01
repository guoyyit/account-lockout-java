package com.github.guoyyit.accountlockoutjava.model;

import java.util.ArrayList;
import java.util.List;

public class AccountState {

    private List<Long> failureTimes = new ArrayList<>();
    private int lockLevel;
    private long lockedUntil;

    public List<Long> getFailureTimes() {
        return failureTimes;
    }

    public void setFailureTimes(List<Long> failureTimes) {
        this.failureTimes = failureTimes;
    }

    public int getLockLevel() {
        return lockLevel;
    }

    public void setLockLevel(int lockLevel) {
        this.lockLevel = lockLevel;
    }

    public long getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(long lockedUntil) {
        this.lockedUntil = lockedUntil;
    }
}
