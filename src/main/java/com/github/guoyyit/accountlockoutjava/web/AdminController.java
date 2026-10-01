package com.github.guoyyit.accountlockoutjava.web;

import com.github.guoyyit.accountlockoutjava.model.AuditEventType;
import com.github.guoyyit.accountlockoutjava.model.AuditRecord;
import com.github.guoyyit.accountlockoutjava.model.UnlockStatus;
import com.github.guoyyit.accountlockoutjava.security.AdminAuthorizer;
import com.github.guoyyit.accountlockoutjava.service.LockoutService;
import com.github.guoyyit.accountlockoutjava.web.dto.UnlockRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final LockoutService lockoutService;
    private final AdminAuthorizer adminAuthorizer;

    public AdminController(LockoutService lockoutService, AdminAuthorizer adminAuthorizer) {
        this.lockoutService = lockoutService;
        this.adminAuthorizer = adminAuthorizer;
    }

    @PostMapping("/unlock")
    public ResponseEntity<String> unlock(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                         @RequestBody UnlockRequest request) {
        UnlockStatus status = lockoutService.unlock(request.account(), token);
        return switch (status) {
            case UNLOCKED -> ResponseEntity.ok("{\"status\":\"UNLOCKED\"}");
            case NOT_LOCKED -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("{\"status\":\"NOT_LOCKED\",\"message\":\"Account is not locked\"}");
            case UNAUTHORIZED -> ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("{\"status\":\"UNAUTHORIZED\",\"message\":\"Admin permission required\"}");
        };
    }

    @GetMapping("/audit")
    public ResponseEntity<?> audit(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestParam(required = false) String account,
            @RequestParam(required = false) AuditEventType type) {
        if (!adminAuthorizer.isAuthorized(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("{\"status\":\"UNAUTHORIZED\",\"message\":\"Admin permission required\"}");
        }
        List<AuditRecord> records = lockoutService.queryAudit(account, type);
        return ResponseEntity.ok(new AuditResponse(records));
    }

    private record AuditResponse(List<AuditRecord> events) {
    }
}
