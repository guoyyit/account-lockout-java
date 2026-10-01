package com.github.guoyyit.accountlockoutjava.model;

import java.util.ArrayList;
import java.util.List;

public class SourceState {

    private List<Long> failureTimes = new ArrayList<>();

    public List<Long> getFailureTimes() {
        return failureTimes;
    }

    public void setFailureTimes(List<Long> failureTimes) {
        this.failureTimes = failureTimes;
    }
}
