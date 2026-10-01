package com.github.guoyyit.accountlockoutjava.domain;

public record AttemptResult(Outcome outcome, ErrorCode errorCode) {

    public enum Outcome {
        SUCCESS,
        FAILURE
    }

    public static AttemptResult success() {
        return new AttemptResult(Outcome.SUCCESS, null);
    }

    public static AttemptResult failure(ErrorCode errorCode) {
        return new AttemptResult(Outcome.FAILURE, errorCode);
    }
}
