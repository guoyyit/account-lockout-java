package com.github.guoyyit.accountlockoutjava.audit;

import com.github.guoyyit.accountlockoutjava.domain.AuditEvent;

import java.util.List;

public interface AuditLogger {

    AuditEvent record(String type, String username, String source, Integer level, String detail);

    List<AuditEvent> query(String username, int limit);
}
