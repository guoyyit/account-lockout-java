package com.github.guoyyit.accountlockoutjava.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lockout")
public class LockoutProperties {

    private Account account = new Account();
    private Source source = new Source();
    private String storageFile = "data/lockout-state.json";
    private Map<String, String> users = new HashMap<>();
    private List<String> adminTokens = new ArrayList<>();

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        this.source = source;
    }

    public String getStorageFile() {
        return storageFile;
    }

    public void setStorageFile(String storageFile) {
        this.storageFile = storageFile;
    }

    public Map<String, String> getUsers() {
        return users;
    }

    public void setUsers(Map<String, String> users) {
        this.users = users;
    }

    public List<String> getAdminTokens() {
        return adminTokens;
    }

    public void setAdminTokens(List<String> adminTokens) {
        this.adminTokens = adminTokens;
    }

    public static class Account {
        private Duration window = Duration.ofMinutes(5);
        private int threshold = 5;
        private List<Duration> levels = new ArrayList<>(
                List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30)));

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }

        public int getThreshold() {
            return threshold;
        }

        public void setThreshold(int threshold) {
            this.threshold = threshold;
        }

        public List<Duration> getLevels() {
            return levels;
        }

        public void setLevels(List<Duration> levels) {
            this.levels = levels;
        }
    }

    public static class Source {
        private Duration window = Duration.ofMinutes(5);
        private int threshold = 10;

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }

        public int getThreshold() {
            return threshold;
        }

        public void setThreshold(int threshold) {
            this.threshold = threshold;
        }
    }
}
