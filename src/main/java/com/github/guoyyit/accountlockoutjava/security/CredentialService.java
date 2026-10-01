package com.github.guoyyit.accountlockoutjava.security;

public interface CredentialService {

    boolean accountExists(String username);

    boolean credentialsMatch(String username, String password);
}
