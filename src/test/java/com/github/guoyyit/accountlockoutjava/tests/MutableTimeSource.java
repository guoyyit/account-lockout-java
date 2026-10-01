package com.github.guoyyit.accountlockoutjava.tests;

import com.github.guoyyit.accountlockoutjava.service.TimeSource;

public class MutableTimeSource implements TimeSource {

    private long current;

    public MutableTimeSource(long start) {
        this.current = start;
    }

    public void setCurrent(long current) {
        this.current = current;
    }

    public void advanceMillis(long millis) {
        this.current += millis;
    }

    @Override
    public long nowEpochMilli() {
        return current;
    }
}
