/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.huawei.opsfactory.gateway.service.ClusterService;
import com.huawei.opsfactory.gateway.service.HostGroupService;
import com.huawei.opsfactory.gateway.service.HostService;

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
 * Test coverage for Host Controller.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RunWith(SpringRunner.class)
@WebMvcTest(HostController.class)
@Import({GatewayProperties.class, AuthWebFilter.class, UserContextFilter.class})
/**
 * Host Controller Test.
 *
 * @author x00000000
 * @since 2026-05-27
 */
public class HostControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HostService hostService;

    @MockBean
    private com.huawei.opsfactory.gateway.service.BusinessServiceService businessServiceService;

    @MockBean
    private com.huawei.opsfactory.gateway.process.PrewarmService prewarmService;

    @MockBean
    private ClusterService clusterService;

    @MockBean
    private HostGroupService hostGroupService;

    // ── listHosts ────────────────────────────────────────────────

    /**
     * Tests list hosts empty.
     */
    @Test
    public void testListHosts_empty() throws Exception {
        when(hostService.listHosts(any())).thenReturn(List.of());

        mockMvc.perform(get("/gateway/hosts/").header("x-secret-key", "test").header("x-user-id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hosts").isArray())
            .andExpect(jsonPath("$.hosts").isEmpty());
    }

    /**
     * Tests list hosts with hosts.
     */
    @Test
    public void testListHosts_withHosts() throws Exception {
        Map<String, Object> host = new LinkedHashMap<>();
        host.put("id", "host-1");
        host.put("name", "Server1");
        host.put("credential", "***");
        when(hostService.listHosts(any())).thenReturn(List.of(host));

        mockMvc.perform(get("/gateway/hosts/").header("x-secret-key", "test").header("x-user-id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hosts[0].id").value("host-1"))
            .andExpect(jsonPath("$.hosts[0].name").value("Server1"));
    }

    /**
     * Tests list hosts with tags filter.
     */
    @Test
    public void testListHosts_withTagsFilter() throws Exception {
        when(hostService.listHosts(any())).thenReturn(List.of());

        mockMvc
            .perform(get("/gateway/hosts/?tags=RCPA,GMDB").header("x-secret-key", "test").header("x-user-id", "admin"))
            .andExpect(status().isOk());
    }

    // ── getHost ──────────────────────────────────────────────────

    /**
     * Tests get host existing.
     */
    @Test
    public void testGetHost_existing() throws Exception {
        Map<String, Object> host = new LinkedHashMap<>();
        host.put("id", "host-1");
        host.put("name", "Server1");
        host.put("credential", "***");
        when(hostService.getHost("host-1")).thenReturn(host);

        mockMvc.perform(get("/gateway/hosts/host-1").header("x-secret-key", "test").header("x-user-id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.host.id").value("host-1"));
    }

    /**
     * Tests get host not found.
     */
    @Test
    public void testGetHost_notFound() throws Exception {
        when(hostService.getHost("nonexistent")).thenThrow(new IllegalArgumentException("Host not found: nonexistent"));

        mockMvc.perform(get("/gateway/hosts/nonexistent").header("x-secret-key", "test").header("x-user-id", "admin"))
            .andExpect(status().isNotFound());
    }

    // ── createHost ───────────────────────────────────────────────

    /**
     * Tests create host success.
     */
    @Test
    public void testCreateHost_success() throws Exception {
        Map<String, Object> created = new LinkedHashMap<>();
        created.put("id", "new-id");
        created.put("name", "NewHost");
        created.put("credential", "***");
        when(hostService.createHost(any())).thenReturn(created);

        mockMvc
            .perform(post("/gateway/hosts/").header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"NewHost\", \"ip\": \"10.0.0.1\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.host.id").value("new-id"));
    }

    /**
     * Tests create host error.
     */
    @Test
    public void testCreateHost_error() throws Exception {
        when(hostService.createHost(any())).thenThrow(new RuntimeException("Encryption failed"));

        mockMvc
            .perform(post("/gateway/hosts/").header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Host\"}"))
            .andExpect(status().is5xxServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Internal server error"));
    }

    // ── updateHost ───────────────────────────────────────────────

    /**
     * Tests update host success.
     */
    @Test
    public void testUpdateHost_success() throws Exception {
        Map<String, Object> updated = new LinkedHashMap<>();
        updated.put("id", "host-1");
        updated.put("name", "Updated");
        when(hostService.updateHost(eq("host-1"), any())).thenReturn(updated);

        mockMvc
            .perform(put("/gateway/hosts/host-1").header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Updated\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.host.name").value("Updated"));
    }

    /**
     * Tests update host not found.
     */
    @Test
    public void testUpdateHost_notFound() throws Exception {
        when(hostService.updateHost(eq("nonexistent"), any()))
            .thenThrow(new IllegalArgumentException("Host not found: nonexistent"));

        mockMvc.perform(put("/gateway/hosts/nonexistent").header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\": \"Updated\"}")).andExpect(status().isNotFound());
    }

    // ── deleteHost ───────────────────────────────────────────────

    /**
     * Tests delete host success.
     */
    @Test
    public void testDeleteHost_success() throws Exception {
        when(hostService.deleteHost("host-1")).thenReturn(true);

        mockMvc.perform(delete("/gateway/hosts/host-1").header("x-secret-key", "test").header("x-user-id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * Tests delete host not found.
     */
    @Test
    public void testDeleteHost_notFound() throws Exception {
        when(hostService.deleteHost("nonexistent")).thenReturn(false);

        mockMvc
            .perform(delete("/gateway/hosts/nonexistent").header("x-secret-key", "test").header("x-user-id", "admin"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false));
    }

    // ── getTags ──────────────────────────────────────────────────

    /**
     * Tests get tags.
     */
    @Test
    public void testGetTags() throws Exception {
        when(hostService.getAllTags()).thenReturn(List.of("RCPA", "GMDB", "ALL"));

        mockMvc.perform(get("/gateway/hosts/tags").header("x-secret-key", "test").header("x-user-id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tags[0]").value("RCPA"))
            .andExpect(jsonPath("$.tags[1]").value("GMDB"))
            .andExpect(jsonPath("$.tags[2]").value("ALL"));
    }

    // ── testConnectivity ─────────────────────────────────────────

    /**
     * Tests connectivity success.
     */
    @Test
    public void testConnectivity_success() throws Exception {
        Map<String, Object> testResult = new LinkedHashMap<>();
        testResult.put("success", true);
        testResult.put("reachable", true);
        testResult.put("latencyMs", 45);
        when(hostService.testConnection("host-1")).thenReturn(testResult);

        mockMvc.perform(post("/gateway/hosts/host-1/test").header("x-secret-key", "test").header("x-user-id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.reachable").value(true));
    }

    /**
     * Tests connectivity failure.
     */
    @Test
    public void testConnectivity_failure() throws Exception {
        Map<String, Object> testResult = new LinkedHashMap<>();
        testResult.put("success", false);
        testResult.put("error", "Connection refused");
        when(hostService.testConnection("host-1")).thenReturn(testResult);

        mockMvc.perform(post("/gateway/hosts/host-1/test").header("x-secret-key", "test").header("x-user-id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false));
    }

    // ── Auth tests ───────────────────────────────────────────────

    /**
     * Tests list hosts unauthorized no key.
     */
    @Test
    public void testListHosts_unauthorized_noKey() throws Exception {
        mockMvc.perform(get("/gateway/hosts/").header("x-user-id", "admin")).andExpect(status().isUnauthorized());
    }

    /**
     * Tests list hosts succeeds for any authenticated user.
     */
    @Test
    public void testListHosts_succeeds_forAnyUser() throws Exception {
        when(hostService.listHosts(any())).thenReturn(List.of());

        mockMvc.perform(get("/gateway/hosts/").header("x-secret-key", "test").header("x-user-id", "regular-user"))
            .andExpect(status().isOk());
    }

    /**
     * Tests create host succeeds for any authenticated user.
     */
    @Test
    public void testCreateHost_succeeds_forAnyUser() throws Exception {
        Map<String, Object> created = new LinkedHashMap<>();
        created.put("id", "new-id");
        created.put("name", "Host");
        created.put("credential", "***");
        when(hostService.createHost(any())).thenReturn(created);

        mockMvc.perform(post("/gateway/hosts/").header("x-secret-key", "test")
            .header("x-user-id", "regular-user")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\": \"Host\"}")).andExpect(status().isCreated());
    }
}