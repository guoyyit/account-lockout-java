package com.github.guoyyit.accountlockoutjava.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.accountlockoutjava.model.AuditEventType;
import com.github.guoyyit.accountlockoutjava.model.AuditRecord;
import com.github.guoyyit.accountlockoutjava.model.LoginOutcome;
import com.github.guoyyit.accountlockoutjava.model.LoginResult;
import com.github.guoyyit.accountlockoutjava.model.UnlockStatus;
import com.github.guoyyit.accountlockoutjava.security.AdminAuthorizer;
import com.github.guoyyit.accountlockoutjava.service.LockoutService;
import com.github.guoyyit.accountlockoutjava.web.dto.LoginRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {LoginController.class, AdminController.class})
class WebApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LockoutService lockoutService;

    @MockBean
    private AdminAuthorizer adminAuthorizer;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void loginMapsEachOutcomeToDistinctErrorCodeAndHttpStatus() throws Exception {
        when(lockoutService.attempt("a1", "p", "1.1.1.1"))
                .thenReturn(new LoginResult(LoginOutcome.SUCCESS, "Authenticated", 0));
        when(lockoutService.attempt("a2", "p", "1.1.1.1"))
                .thenReturn(new LoginResult(LoginOutcome.BAD_CREDENTIALS,
                        "Invalid username or password", 0));
        when(lockoutService.attempt("a3", "p", "1.1.1.1"))
                .thenReturn(new LoginResult(LoginOutcome.ACCOUNT_LOCKED,
                        "Account is locked", 42_000));
        when(lockoutService.attempt("a4", "p", "1.1.1.1"))
                .thenReturn(new LoginResult(LoginOutcome.SOURCE_BLOCKED,
                        "Source is temporarily blocked", 9_000));

        expectLogin("a1", 200, "SUCCESS");
        expectLogin("a2", 401, "BAD_CREDENTIALS");
        expectLogin("a3", 423, "ACCOUNT_LOCKED");
        expectLogin("a4", 429, "SOURCE_BLOCKED");
    }

    @Test
    void unauthorizedUnlockIsForbidden() throws Exception {
        when(lockoutService.unlock("alice", "forged"))
                .thenReturn(UnlockStatus.UNAUTHORIZED);
        mockMvc.perform(post("/api/admin/unlock")
                        .header("X-Admin-Token", "forged")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"alice\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("UNAUTHORIZED"));
    }

    @Test
    void authorizedUnlockSucceeds() throws Exception {
        when(lockoutService.unlock("alice", "admin"))
                .thenReturn(UnlockStatus.UNLOCKED);
        mockMvc.perform(post("/api/admin/unlock")
                        .header("X-Admin-Token", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"alice\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNLOCKED"));
    }

    @Test
    void auditEndpointRequiresAuthorizationAndReturnsEvents() throws Exception {
        mockMvc.perform(get("/api/admin/audit"))
                .andExpect(status().isForbidden());

        when(adminAuthorizer.isAuthorized("admin")).thenReturn(true);
        when(lockoutService.queryAudit("alice", AuditEventType.LOCK)).thenReturn(List.of(
                new AuditRecord("evt-1", 123L, AuditEventType.LOCK, "alice", "1.1.1.1",
                        null, "Account locked at level 1 for PT1M")));
        mockMvc.perform(get("/api/admin/audit")
                        .header("X-Admin-Token", "admin")
                        .param("account", "alice")
                        .param("type", "LOCK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].eventType").value("LOCK"))
                .andExpect(jsonPath("$.events[0].account").value("alice"));
    }

    private void expectLogin(String username, int httpStatus, String code) throws Exception {
        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(username, "p", "1.1.1.1"))))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(code));
    }
}
