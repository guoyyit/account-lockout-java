package com.github.guoyyit.accountlockoutjava.web.dto;

public record LoginResponse(
        String code,
        String message,
        Long retryAfterMillis) {
}
