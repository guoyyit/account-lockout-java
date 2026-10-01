package com.github.guoyyit.accountlockoutjava.domain;

public class LockoutSnapshot {

    private boolean locked;
    private int level;
    private long lockedUntilEpochMilli;
    private long remainingMillis;
    private int failuresInWindow;
    private int escalationCount;

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public long getLockedUntilEpochMilli() {
        return lockedUntilEpochMilli;
    }

    public void setLockedUntilEpochMilli(long lockedUntilEpochMilli) {
        this.lockedUntilEpochMilli = lockedUntilEpochMilli;
    }

    public long getRemainingMillis() {
        return remainingMillis;
    }

    public void setRemainingMillis(long remainingMillis) {
        this.remainingMillis = remainingMillis;
    }

    public int getFailuresInWindow() {
        return failuresInWindow;
    }

    public void setFailuresInWindow(int failuresInWindow) {
        this.failuresInWindow = failuresInWindow;
    }

    public int getEscalationCount() {
        return escalationCount;
    }

    public void setEscalationCount(int escalationCount) {
        this.escalationCount = escalationCount;
    }
}
