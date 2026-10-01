package com.github.guoyyit.accountlockoutjava.tests;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "lockout.failure-window=10m",
        "lockout.source-threshold=100",
        "lockout.levels[0].failures=3",
        "lockout.levels[0].duration=60s",
        "lockout.storage-dir=target/test-data-web",
        "lockout.admin-token=secret-admin-token",
        "lockout.demo-users=carol:secret"
})
@AutoConfigureMockMvc
class LoginLockoutWebIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void lockedAccountReturnsDistinctErrorCodeEvenWithCorrectCredentials() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/login")
                            .contentType("application/json")
                            .content("{\"username\":\"carol\",\"password\":\"bad\"}"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/login")
                        .contentType("application/json")
                        .content("{\"username\":\"carol\",\"password\":\"bad\"}"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LOCKED"));

        mockMvc.perform(post("/api/login")
                        .contentType("application/json")
                        .content("{\"username\":\"carol\",\"password\":\"secret\"}"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LOCKED"));
    }
}
