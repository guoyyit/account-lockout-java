package com.github.guoyyit.accountlockoutjava.service;

import org.springframework.stereotype.Component;

@Component
public class SystemTimeSource implements TimeSource {

    @Override
    public long nowEpochMilli() {
        return System.currentTimeMillis();
    }
}
