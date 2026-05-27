/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.huawei.opsfactory.gateway.config.GatewayProperties;
import com.huawei.opsfactory.gateway.filter.AuthWebFilter;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.service.CommandWhitelistService;

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
 * Test coverage for Command Whitelist Controller.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RunWith(SpringRunner.class)
@WebMvcTest(CommandWhitelistController.class)
@Import({GatewayProperties.class, AuthWebFilter.class, UserContextFilter.class})
/**
 * Command Whitelist Controller Test.
 *
 * @author x00000000
 * @since 2026-05-27
 */
public class CommandWhitelistControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CommandWhitelistService commandWhitelistService;

    @MockBean
    private com.huawei.opsfactory.gateway.process.PrewarmService prewarmService;

    // ── getWhitelist ─────────────────────────────────────────────

    /**
     * Tests get whitelist.
     */
    @Test
    public void testGetWhitelist() throws Exception {
        Map<String, Object> whitelist = new LinkedHashMap<>();
        whitelist.put("commands", List.of(Map.of("pattern", "ps", "description", "查看进程", "enabled", true),
            Map.of("pattern", "tail", "description", "查看日志", "enabled", true)));
        when(commandWhitelistService.getWhitelist()).thenReturn(whitelist);

        mockMvc.perform(get("/gateway/command-whitelist/").header("x-secret-key", "test").header("x-user-id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.commands").isArray())
            .andExpect(jsonPath("$.commands[0].pattern").value("ps"));
    }

    // ── addCommand ───────────────────────────────────────────────

    /**
     * Tests add command success.
     */
    @Test
    public void testAddCommand_success() throws Exception {
        mockMvc
            .perform(post("/gateway/command-whitelist/").header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pattern\": \"iostat\", \"description\": \"IO统计\", \"enabled\": true}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * Tests add command error.
     */
    @Test
    public void testAddCommand_error() throws Exception {
        doThrow(new RuntimeException("Write failed")).when(commandWhitelistService).addCommand(any());

        mockMvc
            .perform(post("/gateway/command-whitelist/").header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pattern\": \"test\"}"))
            .andExpect(status().is5xxServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Internal server error"));
    }

    // ── updateCommand ────────────────────────────────────────────

    /**
     * Tests update command success.
     */
    @Test
    public void testUpdateCommand_success() throws Exception {
        mockMvc
            .perform(put("/gateway/command-whitelist/ps").header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\": \"updated desc\", \"enabled\": false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * Tests update command not found.
     */
    @Test
    public void testUpdateCommand_notFound() throws Exception {
        doThrow(new IllegalArgumentException("Command pattern not found: unknown")).when(commandWhitelistService)
            .updateCommand(eq("unknown"), any());

        mockMvc
            .perform(put("/gateway/command-whitelist/unknown").header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\": \"test\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false));
    }

    // ── deleteCommand ────────────────────────────────────────────

    /**
     * Tests delete command success.
     */
    @Test
    public void testDeleteCommand_success() throws Exception {
        mockMvc
            .perform(
                delete("/gateway/command-whitelist/ps").header("x-secret-key", "test").header("x-user-id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * Tests delete command not found.
     */
    @Test
    public void testDeleteCommand_notFound() throws Exception {
        doThrow(new IllegalArgumentException("Command pattern not found: unknown")).when(commandWhitelistService)
            .deleteCommand("unknown");

        mockMvc
            .perform(delete("/gateway/command-whitelist/unknown").header("x-secret-key", "test")
                .header("x-user-id", "admin"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false));
    }

    // ── Auth tests ───────────────────────────────────────────────

    /**
     * Tests get whitelist unauthorized no key.
     */
    @Test
    public void testGetWhitelist_unauthorized_noKey() throws Exception {
        mockMvc.perform(get("/gateway/command-whitelist/").header("x-user-id", "admin"))
            .andExpect(status().isUnauthorized());
    }

    /**
     * Tests get whitelist succeeds for any authenticated user.
     */
    @Test
    public void testGetWhitelist_succeeds_forAnyUser() throws Exception {
        Map<String, Object> whitelist = new LinkedHashMap<>();
        whitelist.put("commands", List.of(Map.of("pattern", "ps", "description", "查看进程", "enabled", true)));
        when(commandWhitelistService.getWhitelist()).thenReturn(whitelist);

        mockMvc
            .perform(
                get("/gateway/command-whitelist/").header("x-secret-key", "test").header("x-user-id", "regular-user"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.commands").isArray())
            .andExpect(jsonPath("$.commands[0].pattern").value("ps"));
    }

    /**
     * Tests add command succeeds for any authenticated user.
     */
    @Test
    public void testAddCommand_succeeds_forAnyUser() throws Exception {
        mockMvc
            .perform(post("/gateway/command-whitelist/").header("x-secret-key", "test")
                .header("x-user-id", "regular-user")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pattern\": \"test\", \"description\": \"Test command\", \"enabled\": true}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true));
    }
}