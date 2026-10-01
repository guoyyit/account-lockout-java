package com.github.guoyyit.accountlockoutjava.store;

public interface StateStore {

    LockoutState state();

    void persist();
}
