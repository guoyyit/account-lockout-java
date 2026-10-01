package com.github.guoyyit.accountlockoutjava.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.accountlockoutjava.config.LockoutProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
public class FileStateStore implements StateStore {

    private final Path stateFile;
    private final Path tempFile;
    private final ObjectMapper objectMapper;
    private LockoutState cachedState;

    public FileStateStore(LockoutProperties properties, ObjectMapper objectMapper) {
        this.stateFile = Path.of(properties.getStorageDir(), "state.json");
        this.tempFile = Path.of(properties.getStorageDir(), "state.json.tmp");
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(stateFile.getParent());
            this.cachedState = readFromDisk();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot create storage directory", e);
        }
    }

    @Override
    public LockoutState state() {
        if (cachedState == null) {
            synchronized (this) {
                if (cachedState == null) {
                    cachedState = readFromDisk();
                }
            }
        }
        return cachedState;
    }

    @Override
    public synchronized void persist() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), cachedState);
            try {
                Files.move(tempFile, stateFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailed) {
                Files.move(tempFile, stateFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot persist lockout state", e);
        }
    }

    private LockoutState readFromDisk() {
        if (!Files.exists(stateFile)) {
            return new LockoutState();
        }
        try {
            LockoutState state = objectMapper.readValue(stateFile.toFile(), LockoutState.class);
            return state != null ? state : new LockoutState();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read lockout state", e);
        }
    }
}
