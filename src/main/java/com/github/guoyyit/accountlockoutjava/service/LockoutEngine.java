package com.github.guoyyit.accountlockoutjava.service;

import com.github.guoyyit.accountlockoutjava.domain.AttemptResult;
import com.github.guoyyit.accountlockoutjava.domain.LockoutSnapshot;

public interface LockoutEngine {

    AttemptResult attempt(String username, String password, String source);

    void unlock(String username, String operator);

    LockoutSnapshot snapshot(String username);
}
