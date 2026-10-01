package com.github.guoyyit.accountlockoutjava.web;

import com.github.guoyyit.accountlockoutjava.service.LockoutEngine;
import com.github.guoyyit.accountlockoutjava.domain.AttemptResult;
import com.github.guoyyit.accountlockoutjava.domain.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class LoginController {

    private final LockoutEngine engine;

    public LoginController(LockoutEngine engine) {
        this.engine = engine;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String source = SourceResolver.resolve(httpRequest);
        AttemptResult result = engine.attempt(request.username(), request.password(), source);
        if (result.outcome() == AttemptResult.Outcome.SUCCESS) {
            return ResponseEntity.ok(Map.of("status", "SUCCESS"));
        }
        ErrorCode code = result.errorCode();
        if (code == ErrorCode.ACCOUNT_LOCKED) {
            return ResponseEntity.status(HttpStatus.LOCKED)
                    .body(new ErrorResponse("ACCOUNT_LOCKED", "account is temporarily locked"));
        }
        if (code == ErrorCode.SOURCE_BLOCKED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("SOURCE_BLOCKED", "requests from this source are temporarily blocked"));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("INVALID_CREDENTIALS", "invalid username or password"));
    }

    public record LoginRequest(String username, String password) {
    }
}
