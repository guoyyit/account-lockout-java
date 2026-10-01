package com.github.guoyyit.accountlockoutjava.web;

import com.github.guoyyit.accountlockoutjava.audit.AuditLogger;
import com.github.guoyyit.accountlockoutjava.domain.AuditEvent;
import com.github.guoyyit.accountlockoutjava.config.LockoutProperties;
import com.github.guoyyit.accountlockoutjava.service.LockoutEngine;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final LockoutEngine engine;
    private final AuditLogger auditLogger;
    private final LockoutProperties properties;

    public AdminController(LockoutEngine engine, AuditLogger auditLogger, LockoutProperties properties) {
        this.engine = engine;
        this.auditLogger = auditLogger;
        this.properties = properties;
    }

    @PostMapping("/unlock")
    public ResponseEntity<?> unlock(@RequestParam String username,
                                    @RequestParam(required = false) String operator,
                                    HttpServletRequest request) {
        if (!isAuthorized(request)) {
            return forbidden();
        }
        String resolvedOperator = operator != null && !operator.isBlank() ? operator : "admin";
        engine.unlock(username, resolvedOperator);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/audit")
    public ResponseEntity<?> audit(@RequestParam(required = false) String username,
                        @RequestParam(defaultValue = "100") int limit,
                        HttpServletRequest request) {
        if (!isAuthorized(request)) {
            return forbidden();
        }
        List<AuditEvent> events = auditLogger.query(username, limit);
        return ResponseEntity.ok(events);
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(@RequestParam String username, HttpServletRequest request) {
        if (!isAuthorized(request)) {
            return forbidden();
        }
        return ResponseEntity.ok(engine.snapshot(username));
    }

    private boolean isAuthorized(HttpServletRequest request) {
        String expected = properties.getAdminToken();
        if (expected == null || expected.isBlank()) {
            return false;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return false;
        }
        return expected.equals(authorization.substring("Bearer ".length()).trim());
    }

    private ResponseEntity<ErrorResponse> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("FORBIDDEN", "admin authorization required"));
    }
}
