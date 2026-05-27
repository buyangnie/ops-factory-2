/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.huawei.opsfactory.gateway.config.GatewayProperties;
import com.huawei.opsfactory.gateway.filter.AuthWebFilter;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.service.CommandWhitelistService;
import com.huawei.opsfactory.gateway.service.RemoteExecutionService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Test coverage for Remote Exec Controller.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RunWith(SpringRunner.class)
@WebMvcTest(RemoteExecController.class)
@Import({GatewayProperties.class, AuthWebFilter.class, UserContextFilter.class})
/**
 * Remote Exec Controller Test.
 *
 * @author x00000000
 * @since 2026-05-27
 */
public class RemoteExecControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RemoteExecutionService remoteExecutionService;

    @MockBean
    private com.huawei.opsfactory.gateway.process.PrewarmService prewarmService;

    @MockBean
    private CommandWhitelistService commandWhitelistService;

    // ── execute: validation ──────────────────────────────────────

    /**
     * Tests execute missing host id.
     */
    @Test
    public void testExecute_missingHostId() throws Exception {
        mockMvc
            .perform(post("/gateway/remote/execute").header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"command\": \"ps -ef\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("hostId is required"));
    }

    /**
     * Tests execute blank host id.
     */
    @Test
    public void testExecute_blankHostId() throws Exception {
        mockMvc
            .perform(post("/gateway/remote/execute").header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hostId\": \"  \", \"command\": \"ps -ef\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("hostId is required"));
    }

    /**
     * Tests execute missing command.
     */
    @Test
    public void testExecute_missingCommand() throws Exception {
        mockMvc
            .perform(post("/gateway/remote/execute").header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hostId\": \"host-1\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("command is required"));
    }

    /**
     * Tests execute blank command.
     */
    @Test
    public void testExecute_blankCommand() throws Exception {
        mockMvc
            .perform(post("/gateway/remote/execute").header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hostId\": \"host-1\", \"command\": \"  \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("command is required"));
    }

    // ── execute: success ─────────────────────────────────────────

    /**
     * Tests execute success.
     */
    @Test
    public void testExecute_success() throws Exception {
        Map<String, Object> execResult = new LinkedHashMap<>();
        execResult.put("hostId", "host-1");
        execResult.put("hostName", "Server1");
        execResult.put("exitCode", 0);
        execResult.put("output", "rcpa  1234  1  0  Mar27 ?  00:05:23 /rcpa/openas");
        execResult.put("error", "");
        execResult.put("duration", 1250L);
        when(remoteExecutionService.execute("host-1", "ps -ef", 30)).thenReturn(execResult);

        mockMvc
            .perform(post("/gateway/remote/execute").header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hostId\": \"host-1\", \"command\": \"ps -ef\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hostId").value("host-1"))
            .andExpect(jsonPath("$.exitCode").value(0))
            .andExpect(jsonPath("$.output").value("rcpa  1234  1  0  Mar27 ?  00:05:23 /rcpa/openas"))
            .andExpect(jsonPath("$.duration").value(1250));
    }

    /**
     * Tests execute custom timeout.
     */
    @Test
    public void testExecute_customTimeout() throws Exception {
        Map<String, Object> execResult = new LinkedHashMap<>();
        execResult.put("hostId", "host-1");
        execResult.put("hostName", "Server1");
        execResult.put("exitCode", 0);
        execResult.put("output", "ok");
        execResult.put("error", "");
        execResult.put("duration", 100L);
        when(remoteExecutionService.execute("host-1", "ls", 60)).thenReturn(execResult);

        mockMvc
            .perform(post("/gateway/remote/execute").header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hostId\": \"host-1\", \"command\": \"ls\", \"timeout\": 60}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.exitCode").value(0));
    }

    // ── execute: whitelist rejection ─────────────────────────────

    /**
     * Tests execute whitelist rejected.
     */
    @Test
    public void testExecute_whitelistRejected() throws Exception {
        Map<String, Object> execResult = new LinkedHashMap<>();
        execResult.put("hostId", "host-1");
        execResult.put("hostName", "Server1");
        execResult.put("exitCode", -1);
        execResult.put("output", "");
        execResult.put("error", "Command rejected: the following commands are not in the whitelist: rm");
        execResult.put("rejectedCommands", List.of("rm"));
        execResult.put("duration", 0L);
        when(remoteExecutionService.execute("host-1", "rm -rf /", 30)).thenReturn(execResult);

        mockMvc
            .perform(post("/gateway/remote/execute").header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hostId\": \"host-1\", \"command\": \"rm -rf /\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.exitCode").value(-1));
    }

    /**
     * Tests execute whitelist rejected with success false.
     */
    @Test
    public void testExecute_whitelistRejected_withSuccessFalse() throws Exception {
        Map<String, Object> execResult = new LinkedHashMap<>();
        execResult.put("success", Boolean.FALSE);
        execResult.put("hostId", "host-1");
        execResult.put("hostName", "Server1");
        execResult.put("exitCode", -1);
        execResult.put("output", "");
        execResult.put("error", "Command rejected");
        execResult.put("rejectedCommands", List.of("rm"));
        execResult.put("duration", 0L);
        when(remoteExecutionService.execute("host-1", "rm -rf /", 30)).thenReturn(execResult);

        mockMvc
            .perform(post("/gateway/remote/execute").header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hostId\": \"host-1\", \"command\": \"rm -rf /\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Command rejected by whitelist"));
    }

    // ── execute: host not found ──────────────────────────────────

    /**
     * Tests execute host not found.
     */
    @Test
    public void testExecute_hostNotFound() throws Exception {
        Map<String, Object> execResult = new LinkedHashMap<>();
        execResult.put("hostId", "nonexistent");
        execResult.put("hostName", "");
        execResult.put("exitCode", -1);
        execResult.put("output", "");
        execResult.put("error", "Host not found: nonexistent");
        execResult.put("duration", 0L);
        when(remoteExecutionService.execute("nonexistent", "ls", 30)).thenReturn(execResult);

        mockMvc
            .perform(post("/gateway/remote/execute").header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hostId\": \"nonexistent\", \"command\": \"ls\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.exitCode").value(-1));
    }

    /**
     * Tests execute unexpected failure is sanitized.
     */
    @Test
    public void testExecute_unexpectedFailure_isSanitized() throws Exception {
        when(remoteExecutionService.execute("host-1", "ls", 30)).thenThrow(new RuntimeException("SSH stack trace"));

        mockMvc
            .perform(post("/gateway/remote/execute").header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hostId\": \"host-1\", \"command\": \"ls\"}"))
            .andExpect(status().is5xxServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Internal server error"));
    }

    // ── Auth tests ───────────────────────────────────────────────

    /**
     * Tests execute unauthorized no key.
     */
    @Test
    public void testExecute_unauthorized_noKey() throws Exception {
        mockMvc.perform(post("/gateway/remote/execute").header("x-user-id", "admin")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"hostId\": \"host-1\", \"command\": \"ls\"}")).andExpect(status().isUnauthorized());
    }

    /**
     * Tests execute succeeds for any authenticated user.
     */
    @Test
    public void testExecute_succeeds_forAnyUser() throws Exception {
        Map<String, Object> execResult = new LinkedHashMap<>();
        execResult.put("hostId", "host-1");
        execResult.put("hostName", "Server1");
        execResult.put("exitCode", 0);
        execResult.put("output", "ok");
        execResult.put("error", "");
        execResult.put("duration", 100L);
        when(remoteExecutionService.execute("host-1", "ls", 30)).thenReturn(execResult);

        mockMvc.perform(post("/gateway/remote/execute").header("x-secret-key", "test")
            .header("x-user-id", "regular-user")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"hostId\": \"host-1\", \"command\": \"ls\"}")).andExpect(status().isOk());
    }
}