package com.github.guoyyit.accountlockoutjava.domain;

import java.util.ArrayDeque;
import java.util.Deque;

public class SourceState {

    private Deque<Long> failureTimes = new ArrayDeque<>();

    public Deque<Long> getFailureTimes() {
        return failureTimes;
    }

    public void setFailureTimes(Deque<Long> failureTimes) {
        this.failureTimes = failureTimes;
    }
}
