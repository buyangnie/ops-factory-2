/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import com.huawei.opsfactory.gateway.service.BusinessServiceService;

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
 * Test coverage for Business Service Controller.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RunWith(SpringRunner.class)
@WebMvcTest(BusinessServiceController.class)
@Import({GatewayProperties.class, AuthWebFilter.class, UserContextFilter.class})
/**
 * Business Service Controller Test.
 *
 * @author x00000000
 * @since 2026-05-27
 */
public class BusinessServiceControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BusinessServiceService businessServiceService;

    @MockBean
    private com.huawei.opsfactory.gateway.process.PrewarmService prewarmService;

    // ── listBusinessServices ───────────────────────────────────────

    /**
     * Tests list business services.
     */
    @Test
    public void testListBusinessServices() throws Exception {
        when(businessServiceService.listBusinessServices(isNull(), isNull())).thenReturn(List.of());

        mockMvc.perform(get("/gateway/business-services").header("x-secret-key", "test").header("x-user-id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.businessServices").isArray())
            .andExpect(jsonPath("$.businessServices").isEmpty());
    }

    /**
     * Tests list business services with keyword.
     */
    @Test
    public void testListBusinessServices_withKeyword() throws Exception {
        Map<String, Object> bs = new LinkedHashMap<>();
        bs.put("id", "bs-1");
        bs.put("name", "OrderService");
        when(businessServiceService.searchByKeyword("order")).thenReturn(List.of(bs));

        mockMvc
            .perform(get("/gateway/business-services?keyword=order").header("x-secret-key", "test")
                .header("x-user-id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.businessServices[0].id").value("bs-1"));
    }

    // ── getBusinessService ─────────────────────────────────────────

    /**
     * Tests get business service.
     */
    @Test
    public void testGetBusinessService() throws Exception {
        Map<String, Object> bs = new LinkedHashMap<>();
        bs.put("id", "bs-1");
        bs.put("name", "OrderService");
        when(businessServiceService.getBusinessService("bs-1")).thenReturn(bs);

        mockMvc
            .perform(get("/gateway/business-services/bs-1").header("x-secret-key", "test").header("x-user-id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.businessService.id").value("bs-1"));
    }

    /**
     * Tests get business service not found.
     */
    @Test
    public void testGetBusinessService_notFound() throws Exception {
        when(businessServiceService.getBusinessService("nonexistent"))
            .thenThrow(new IllegalArgumentException("Business service not found: nonexistent"));

        mockMvc
            .perform(get("/gateway/business-services/nonexistent").header("x-secret-key", "test")
                .header("x-user-id", "admin"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false));
    }

    // ── getResolved ────────────────────────────────────────────────

    /**
     * Tests get business service resolved.
     */
    @Test
    public void testGetBusinessServiceResolved() throws Exception {
        Map<String, Object> resolved = new LinkedHashMap<>();
        resolved.put("id", "bs-1");
        resolved.put("name", "OrderService");
        resolved.put("resolvedHosts", List.of());
        resolved.put("totalHostCount", 0);
        when(businessServiceService.getWithResolvedHosts("bs-1")).thenReturn(resolved);

        mockMvc
            .perform(get("/gateway/business-services/bs-1/resolved").header("x-secret-key", "test")
                .header("x-user-id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.businessService.id").value("bs-1"));
    }

    // ── getHosts ───────────────────────────────────────────────────

    /**
     * Tests get business service hosts.
     */
    @Test
    public void testGetBusinessServiceHosts() throws Exception {
        Map<String, Object> host = new LinkedHashMap<>();
        host.put("id", "host-1");
        host.put("name", "Server1");
        when(businessServiceService.getHostsForBusinessService("bs-1")).thenReturn(List.of(host));

        mockMvc
            .perform(get("/gateway/business-services/bs-1/hosts").header("x-secret-key", "test")
                .header("x-user-id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hosts[0].id").value("host-1"));
    }

    // ── getTopology ────────────────────────────────────────────────

    /**
     * Tests get business service topology.
     */
    @Test
    public void testGetBusinessServiceTopology() throws Exception {
        Map<String, Object> topology = new LinkedHashMap<>();
        topology.put("nodes", List.of());
        topology.put("edges", List.of());
        when(businessServiceService.getTopologyForBusinessService("bs-1")).thenReturn(topology);

        mockMvc
            .perform(get("/gateway/business-services/bs-1/topology").header("x-secret-key", "test")
                .header("x-user-id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nodes").isArray())
            .andExpect(jsonPath("$.edges").isArray());
    }

    // ── createBusinessService ──────────────────────────────────────

    /**
     * Tests create business service.
     */
    @Test
    public void testCreateBusinessService() throws Exception {
        Map<String, Object> created = new LinkedHashMap<>();
        created.put("id", "new-id");
        created.put("name", "NewService");
        when(businessServiceService.createBusinessService(any())).thenReturn(created);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "NewService");

        mockMvc
            .perform(post("/gateway/business-services").header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"NewService\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.businessService.id").value("new-id"));
    }

    /**
     * Tests create business service error.
     */
    @Test
    public void testCreateBusinessService_error() throws Exception {
        when(businessServiceService.createBusinessService(any())).thenThrow(new RuntimeException("Creation failed"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "FailService");

        mockMvc
            .perform(post("/gateway/business-services").header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"FailService\"}"))
            .andExpect(status().is5xxServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Internal server error"));
    }

    // ── updateBusinessService ──────────────────────────────────────

    /**
     * Tests update business service.
     */
    @Test
    public void testUpdateBusinessService() throws Exception {
        Map<String, Object> updated = new LinkedHashMap<>();
        updated.put("id", "bs-1");
        updated.put("name", "UpdatedService");
        when(businessServiceService.updateBusinessService(eq("bs-1"), any())).thenReturn(updated);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "UpdatedService");

        mockMvc
            .perform(put("/gateway/business-services/bs-1").header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"UpdatedService\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.businessService.name").value("UpdatedService"));
    }

    /**
     * Tests update business service not found.
     */
    @Test
    public void testUpdateBusinessService_notFound() throws Exception {
        when(businessServiceService.updateBusinessService(eq("nonexistent"), any()))
            .thenThrow(new IllegalArgumentException("Business service not found: nonexistent"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Updated");

        mockMvc
            .perform(put("/gateway/business-services/nonexistent").header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Updated\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false));
    }

    // ── deleteBusinessService ──────────────────────────────────────

    /**
     * Tests delete business service.
     */
    @Test
    public void testDeleteBusinessService() throws Exception {
        when(businessServiceService.deleteBusinessService("bs-1")).thenReturn(true);

        mockMvc
            .perform(
                delete("/gateway/business-services/bs-1").header("x-secret-key", "test").header("x-user-id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * Tests delete business service not found.
     */
    @Test
    public void testDeleteBusinessService_notFound() throws Exception {
        when(businessServiceService.deleteBusinessService("nonexistent")).thenReturn(false);

        mockMvc
            .perform(delete("/gateway/business-services/nonexistent").header("x-secret-key", "test")
                .header("x-user-id", "admin"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false));
    }

    // ── migrate ────────────────────────────────────────────────────

    /**
     * Tests migrate.
     */
    @Test
    public void testMigrate() throws Exception {
        Map<String, Object> migrateResult = new LinkedHashMap<>();
        migrateResult.put("migrated", 2);
        migrateResult.put("businessServices", List.of());
        when(businessServiceService.migrateFromBusinessField()).thenReturn(migrateResult);

        mockMvc
            .perform(
                post("/gateway/business-services/migrate").header("x-secret-key", "test").header("x-user-id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.migrated").value(2));
    }

    // ── Auth tests ─────────────────────────────────────────────────

    /**
     * Tests list business services unauthorized no key.
     */
    @Test
    public void testListBusinessServices_unauthorized_noKey() throws Exception {
        mockMvc.perform(get("/gateway/business-services").header("x-user-id", "admin"))
            .andExpect(status().isUnauthorized());
    }

    /**
     * Tests list business services succeeds for any authenticated user.
     */
    @Test
    public void testListBusinessServices_succeeds_forAnyUser() throws Exception {
        when(businessServiceService.listBusinessServices(isNull(), isNull())).thenReturn(List.of());

        mockMvc
            .perform(
                get("/gateway/business-services").header("x-secret-key", "test").header("x-user-id", "regular-user"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.businessServices").isArray())
            .andExpect(jsonPath("$.businessServices").isEmpty());
    }

    /**
     * Tests create business service succeeds for any authenticated user.
     */
    @Test
    public void testCreateBusinessService_succeeds_forAnyUser() throws Exception {
        Map<String, Object> created = new LinkedHashMap<>();
        created.put("id", "new-id");
        created.put("name", "Service");
        when(businessServiceService.createBusinessService(any())).thenReturn(created);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Service");

        mockMvc
            .perform(post("/gateway/business-services").header("x-secret-key", "test")
                .header("x-user-id", "regular-user")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Service\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.businessService.id").value("new-id"));
    }
}