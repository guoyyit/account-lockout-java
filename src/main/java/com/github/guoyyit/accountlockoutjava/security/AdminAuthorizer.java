package com.github.guoyyit.accountlockoutjava.security;

import com.github.guoyyit.accountlockoutjava.config.LockoutProperties;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AdminAuthorizer {

    private final List<String> adminTokens;

    public AdminAuthorizer(LockoutProperties properties) {
        this.adminTokens = List.copyOf(properties.getAdminTokens());
    }

    public boolean isAuthorized(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return adminTokens.stream().anyMatch(token::equals);
    }
}
