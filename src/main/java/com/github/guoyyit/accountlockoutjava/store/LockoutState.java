package com.github.guoyyit.accountlockoutjava.store;

import com.github.guoyyit.accountlockoutjava.domain.AccountState;
import com.github.guoyyit.accountlockoutjava.domain.AuditEvent;
import com.github.guoyyit.accountlockoutjava.domain.SourceState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LockoutState {

    private Map<String, AccountState> accounts = new HashMap<>();
    private Map<String, SourceState> sources = new HashMap<>();
    private long auditSequence;
    private List<AuditEvent> events = new ArrayList<>();

    public Map<String, AccountState> getAccounts() {
        return accounts;
    }

    public void setAccounts(Map<String, AccountState> accounts) {
        this.accounts = accounts;
    }

    public Map<String, SourceState> getSources() {
        return sources;
    }

    public void setSources(Map<String, SourceState> sources) {
        this.sources = sources;
    }

    public long getAuditSequence() {
        return auditSequence;
    }

    public void setAuditSequence(long auditSequence) {
        this.auditSequence = auditSequence;
    }

    public List<AuditEvent> getEvents() {
        return events;
    }

    public void setEvents(List<AuditEvent> events) {
        this.events = events != null ? events : new ArrayList<>();
    }
}
