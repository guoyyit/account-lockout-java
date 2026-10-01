package com.github.guoyyit.accountlockoutjava.web.dto;

public record LoginRequest(
        String username,
        String password,
        String source) {
}
