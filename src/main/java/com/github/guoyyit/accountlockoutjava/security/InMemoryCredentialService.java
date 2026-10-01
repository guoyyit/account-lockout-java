package com.github.guoyyit.accountlockoutjava.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class InMemoryCredentialService implements CredentialService {

    private final Map<String, String> users = new HashMap<>();

    public InMemoryCredentialService(@Value("${lockout.demo-users:alice:password1,bob:password2}") String userList) {
        if (userList != null && !userList.isBlank()) {
            for (String entry : userList.split(",")) {
                String trimmed = entry.trim();
                int separator = trimmed.indexOf(':');
                if (separator > 0) {
                    users.put(trimmed.substring(0, separator), trimmed.substring(separator + 1));
                }
            }
        }
    }

    @Override
    public boolean accountExists(String username) {
        return users.containsKey(username);
    }

    @Override
    public boolean credentialsMatch(String username, String password) {
        String expected = users.get(username);
        return expected != null && expected.equals(password);
    }
}
