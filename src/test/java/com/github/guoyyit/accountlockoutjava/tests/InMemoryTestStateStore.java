package com.github.guoyyit.accountlockoutjava.tests;

import com.github.guoyyit.accountlockoutjava.store.LockoutState;
import com.github.guoyyit.accountlockoutjava.store.StateStore;

public class InMemoryTestStateStore implements StateStore {

    private LockoutState state = new LockoutState();

    @Override
    public LockoutState state() {
        return state;
    }

    @Override
    public void persist() {
    }

    public void setState(LockoutState state) {
        this.state = state;
    }
}
