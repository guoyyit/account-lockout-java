package com.github.guoyyit.accountlockoutjava.domain;

import java.time.Instant;

public record AuditEvent(
        long sequence,
        Instant time,
        String type,
        String username,
        String source,
        Integer level,
        String detail
) {
}
