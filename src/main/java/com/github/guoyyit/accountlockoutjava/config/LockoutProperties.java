package com.github.guoyyit.accountlockoutjava.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "lockout")
public class LockoutProperties {

    private Duration failureWindow = Duration.ofMinutes(15);
    private int sourceThreshold = 10;
    private List<LockLevel> levels = List.of();
    private String storageDir = "data/lockout";
    private String adminToken = "";

    public Duration getFailureWindow() {
        return failureWindow;
    }

    public void setFailureWindow(Duration failureWindow) {
        this.failureWindow = failureWindow;
    }

    public int getSourceThreshold() {
        return sourceThreshold;
    }

    public void setSourceThreshold(int sourceThreshold) {
        this.sourceThreshold = sourceThreshold;
    }

    public List<LockLevel> getLevels() {
        return levels;
    }

    public void setLevels(List<LockLevel> levels) {
        this.levels = levels;
    }

    public String getStorageDir() {
        return storageDir;
    }

    public void setStorageDir(String storageDir) {
        this.storageDir = storageDir;
    }

    public String getAdminToken() {
        return adminToken;
    }

    public void setAdminToken(String adminToken) {
        this.adminToken = adminToken;
    }

    public static class LockLevel {

        private int failures;
        private Duration duration = Duration.ZERO;

        public int getFailures() {
            return failures;
        }

        public void setFailures(int failures) {
            this.failures = failures;
        }

        public Duration getDuration() {
            return duration;
        }

        public void setDuration(Duration duration) {
            this.duration = duration;
        }
    }
}
