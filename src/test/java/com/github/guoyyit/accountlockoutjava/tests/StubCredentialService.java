package com.github.guoyyit.accountlockoutjava.tests;

import com.github.guoyyit.accountlockoutjava.security.CredentialService;

import java.util.HashSet;
import java.util.Set;

public class StubCredentialService implements CredentialService {

    private final Set<String> existingAccounts = new HashSet<>();

    public StubCredentialService addAccount(String username) {
        existingAccounts.add(username);
        return this;
    }

    @Override
    public boolean accountExists(String username) {
        return existingAccounts.contains(username);
    }

    @Override
    public boolean credentialsMatch(String username, String password) {
        return existingAccounts.contains(username) && "good".equals(password);
    }
}
