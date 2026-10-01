package com.github.guoyyit.accountlockoutjava.model;

public record LoginResult(
        LoginOutcome outcome,
        String message,
        long retryAfterMillis) {
}
