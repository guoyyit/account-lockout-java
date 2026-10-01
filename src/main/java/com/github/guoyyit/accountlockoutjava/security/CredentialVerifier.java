package com.github.guoyyit.accountlockoutjava.security;

public interface CredentialVerifier {

    enum Status {
        EXISTS_MATCH,
        EXISTS_MISMATCH,
        NOT_FOUND
    }

    Status verify(String username, String password);
}
