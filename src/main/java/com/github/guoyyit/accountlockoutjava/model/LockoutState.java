package com.github.guoyyit.accountlockoutjava.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LockoutState {

    private Map<String, AccountState> accounts = new HashMap<>();
    private Map<String, SourceState> sources = new HashMap<>();
    private List<AuditRecord> audit = new ArrayList<>();

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
    public List<AuditRecord> getAudit() {
        return audit;
    }

    public void setAudit(List<AuditRecord> audit) {
        this.audit = audit;
    }
}
