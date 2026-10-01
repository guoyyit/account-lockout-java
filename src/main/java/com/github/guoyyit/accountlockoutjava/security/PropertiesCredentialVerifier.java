package com.github.guoyyit.accountlockoutjava.security;

import com.github.guoyyit.accountlockoutjava.config.LockoutProperties;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PropertiesCredentialVerifier implements CredentialVerifier {

    private final Map<String, String> users;

    public PropertiesCredentialVerifier(LockoutProperties properties) {
        this.users = Map.copyOf(properties.getUsers());
    }

    @Override
    public Status verify(String username, String password) {
        String expected = users.get(username);
        if (expected == null) {
            return Status.NOT_FOUND;
        }
        if (expected.equals(password)) {
            return Status.EXISTS_MATCH;
        }
        return Status.EXISTS_MISMATCH;
    }
}
