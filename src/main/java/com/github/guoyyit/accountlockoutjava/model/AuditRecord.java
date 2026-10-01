package com.github.guoyyit.accountlockoutjava.model;

public record AuditRecord(
        String id,
        long timestamp,
        AuditEventType eventType,
        String account,
        String source,
        String operator,
        String detail) {
}
