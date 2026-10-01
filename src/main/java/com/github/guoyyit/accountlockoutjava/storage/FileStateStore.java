package com.github.guoyyit.accountlockoutjava.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.accountlockoutjava.config.LockoutProperties;
import com.github.guoyyit.accountlockoutjava.model.LockoutState;
import com.fasterxml.jackson.databind.DeserializationFeature;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.stereotype.Component;

@Component
public class FileStateStore {

    private final Path stateFile;
    private final ObjectMapper objectMapper;

    public FileStateStore(LockoutProperties properties, ObjectMapper objectMapper) {
        this.stateFile = Path.of(properties.getStorageFile());
        this.objectMapper = objectMapper.copy()
                .findAndRegisterModules()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public LockoutState load() {
        if (!Files.isRegularFile(stateFile)) {
            return new LockoutState();
        }
        try {
            LockoutState state = objectMapper.readValue(stateFile.toFile(), LockoutState.class);
            if (state == null) {
                return new LockoutState();
            }
            if (state.getAccounts() == null) {
                state.setAccounts(new java.util.HashMap<>());
            }
            if (state.getSources() == null) {
                state.setSources(new java.util.HashMap<>());
            }
            if (state.getAudit() == null) {
                state.setAudit(new java.util.ArrayList<>());
            }
            return state;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load lockout state from " + stateFile, e);
        }
    }

    public void save(LockoutState state) {
        try {
            Path parent = stateFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = Files.createTempFile(parent, "lockout-state", ".tmp");
            try {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), state);
                Files.move(temp, stateFile, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to persist lockout state to " + stateFile, e);
        }
    }
}
