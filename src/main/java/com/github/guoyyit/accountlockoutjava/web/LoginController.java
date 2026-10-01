package com.github.guoyyit.accountlockoutjava.web;

import com.github.guoyyit.accountlockoutjava.model.LoginOutcome;
import com.github.guoyyit.accountlockoutjava.model.LoginResult;
import com.github.guoyyit.accountlockoutjava.service.LockoutService;
import com.github.guoyyit.accountlockoutjava.web.dto.LoginRequest;
import com.github.guoyyit.accountlockoutjava.web.dto.LoginResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/login")
public class LoginController {

    private final LockoutService lockoutService;

    public LoginController(LockoutService lockoutService) {
        this.lockoutService = lockoutService;
    }

    @PostMapping
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        String source = request.source() == null || request.source().isBlank()
                ? "unknown" : request.source();
        LoginResult result = lockoutService.attempt(request.username(), request.password(), source);
        LoginResponse response = new LoginResponse(result.outcome().name(),
                result.message(), result.retryAfterMillis() > 0 ? result.retryAfterMillis() : null);
        HttpStatus status = switch (result.outcome()) {
            case SUCCESS -> HttpStatus.OK;
            case BAD_CREDENTIALS -> HttpStatus.UNAUTHORIZED;
            case ACCOUNT_LOCKED -> HttpStatus.LOCKED;
            case SOURCE_BLOCKED -> HttpStatus.TOO_MANY_REQUESTS;
        };
        return ResponseEntity.status(status).body(response);
    }
}
