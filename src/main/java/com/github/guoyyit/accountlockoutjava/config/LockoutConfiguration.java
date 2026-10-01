package com.github.guoyyit.accountlockoutjava.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LockoutProperties.class)
public class LockoutConfiguration {

    @PostConstruct
    void validate() {
        if (properties.getFailureWindow().isZero() || properties.getFailureWindow().isNegative()) {
            throw new IllegalStateException("lockout.failure-window must be positive");
        }
        if (properties.getSourceThreshold() <= 0) {
            throw new IllegalStateException("lockout.source-threshold must be positive");
        }
        int previousFailures = 0;
        java.time.Duration previousDuration = java.time.Duration.ZERO;
        for (LockoutProperties.LockLevel level : properties.getLevels()) {
            if (level.getFailures() <= previousFailures) {
                throw new IllegalStateException("lockout levels must declare strictly increasing failure counts");
            }
            if (level.getDuration().compareTo(previousDuration) < 0) {
                throw new IllegalStateException("lockout level durations must not decrease");
            }
            previousFailures = level.getFailures();
            previousDuration = level.getDuration();
        }
    }

    private final LockoutProperties properties;

    public LockoutConfiguration(LockoutProperties properties) {
        this.properties = properties;
    }
}
