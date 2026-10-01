package com.github.guoyyit.accountlockoutjava.tests;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "lockout.failure-window=10m",
        "lockout.source-threshold=100",
        "lockout.levels[0].failures=5",
        "lockout.levels[0].duration=1m",
        "lockout.storage-dir=target/test-data-admin",
        "lockout.admin-token=secret-admin-token",
        "lockout.demo-users=alice:password1"
})
@AutoConfigureMockMvc
class AdminApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unknownAccountAndBadPasswordAreIndistinguishable() throws Exception {
        mockMvc.perform(post("/api/login")
                        .contentType("application/json")
                        .content("{\"username\":\"ghost\",\"password\":\"whatever\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));

        mockMvc.perform(post("/api/login")
                        .contentType("application/json")
                        .content("{\"username\":\"alice\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    void successfulLoginWorks() throws Exception {
        mockMvc.perform(post("/api/login")
                        .contentType("application/json")
                        .content("{\"username\":\"alice\",\"password\":\"password1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void unlockWithoutTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/api/admin/unlock").param("username", "alice"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void unlockWithWrongTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/api/admin/unlock")
                        .param("username", "alice")
                        .header("Authorization", "Bearer nope"))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditWithoutTokenIsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/audit"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authorizedUnlockAndAuditWork() throws Exception {
        mockMvc.perform(post("/api/admin/unlock")
                        .param("username", "alice")
                        .param("operator", "tester")
                        .header("Authorization", "Bearer secret-admin-token"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/audit")
                        .param("username", "alice")
                        .header("Authorization", "Bearer secret-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type=='UNLOCKED')]").exists());
    }
}
