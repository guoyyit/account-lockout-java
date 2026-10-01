package com.github.guoyyit.accountlockoutjava.audit;

import com.github.guoyyit.accountlockoutjava.domain.AuditEvent;
import com.github.guoyyit.accountlockoutjava.store.LockoutState;
import com.github.guoyyit.accountlockoutjava.store.StateStore;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class DefaultAuditLogger implements AuditLogger {

    private static final int DEFAULT_MAX_EVENTS = 10_000;

    private final StateStore stateStore;

    public DefaultAuditLogger(StateStore stateStore) {
        this.stateStore = stateStore;
    }

    @Override
    public AuditEvent record(String type, String username, String source, Integer level, String detail) {
        LockoutState state = stateStore.state();
        long sequence = state.getAuditSequence() + 1;
        state.setAuditSequence(sequence);
        AuditEvent event = new AuditEvent(sequence, Instant.now(), type, username, source, level, detail);
        List<AuditEvent> events = state.getEvents();
        events.add(event);
        trimIfNeeded(events);
        stateStore.persist();
        return event;
    }

    @Override
    public List<AuditEvent> query(String username, int limit) {
        int max = limit <= 0 ? 100 : Math.min(limit, 10_000);
        List<AuditEvent> all = stateStore.state().getEvents();
        List<AuditEvent> matches = new ArrayList<>();
        for (AuditEvent event : all) {
            if (username == null || username.isBlank() || username.equals(event.username())) {
                matches.add(event);
            }
        }
        int from = Math.max(0, matches.size() - max);
        return List.copyOf(matches.subList(from, matches.size()));
    }

    private void trimIfNeeded(List<AuditEvent> events) {
        int max = DEFAULT_MAX_EVENTS;
        while (events.size() > max) {
            events.remove(0);
        }
    }
}
