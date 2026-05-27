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
import com.huawei.opsfactory.gateway.service.SopService;

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
 * Test coverage for Sop Controller.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RunWith(SpringRunner.class)
@WebMvcTest(SopController.class)
@Import({GatewayProperties.class, AuthWebFilter.class, UserContextFilter.class})
/**
 * Sop Controller Test.
 *
 * @author x00000000
 * @since 2026-05-27
 */
public class SopControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SopService sopService;

    @MockBean
    private com.huawei.opsfactory.gateway.process.PrewarmService prewarmService;

    // ── listSops ─────────────────────────────────────────────────

    /**
     * Tests list sops empty.
     */
    @Test
    public void testListSops_empty() throws Exception {
        when(sopService.listSops()).thenReturn(List.of());

        mockMvc.perform(get("/gateway/sops/").header("x-secret-key", "test").header("x-user-id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sops").isArray())
            .andExpect(jsonPath("$.sops").isEmpty());
    }

    /**
     * Tests list sops with data.
     */
    @Test
    public void testListSops_withData() throws Exception {
        Map<String, Object> sop = new LinkedHashMap<>();
        sop.put("id", "sop-1");
        sop.put("name", "RCPA诊断");
        when(sopService.listSops()).thenReturn(List.of(sop));

        mockMvc.perform(get("/gateway/sops/").header("x-secret-key", "test").header("x-user-id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sops[0].id").value("sop-1"))
            .andExpect(jsonPath("$.sops[0].name").value("RCPA诊断"));
    }

    // ── getSop ───────────────────────────────────────────────────

    /**
     * Tests get sop existing.
     */
    @Test
    public void testGetSop_existing() throws Exception {
        Map<String, Object> sop = new LinkedHashMap<>();
        sop.put("id", "sop-1");
        sop.put("name", "TestSOP");
        sop.put("nodes", List.of());
        when(sopService.getSop("sop-1")).thenReturn(sop);

        mockMvc.perform(get("/gateway/sops/sop-1").header("x-secret-key", "test").header("x-user-id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.sop.id").value("sop-1"));
    }

    /**
     * Tests get sop not found.
     */
    @Test
    public void testGetSop_notFound() throws Exception {
        when(sopService.getSop("nonexistent")).thenThrow(new IllegalArgumentException("SOP not found: nonexistent"));

        mockMvc.perform(get("/gateway/sops/nonexistent").header("x-secret-key", "test").header("x-user-id", "admin"))
            .andExpect(status().is5xxServerError());
    }

    // ── createSop ────────────────────────────────────────────────

    /**
     * Tests create sop success.
     */
    @Test
    public void testCreateSop_success() throws Exception {
        Map<String, Object> created = new LinkedHashMap<>();
        created.put("id", "new-id");
        created.put("name", "NewSOP");
        when(sopService.createSop(any())).thenReturn(created);

        mockMvc
            .perform(post("/gateway/sops/").header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"NewSOP\", \"description\": \"Test\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.sop.id").value("new-id"));
    }

    /**
     * Tests create sop error.
     */
    @Test
    public void testCreateSop_error() throws Exception {
        when(sopService.createSop(any())).thenThrow(new RuntimeException("Write failed"));

        mockMvc
            .perform(post("/gateway/sops/").header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"SOP\"}"))
            .andExpect(status().is5xxServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Internal server error"));
    }

    // ── updateSop ────────────────────────────────────────────────

    /**
     * Tests update sop success.
     */
    @Test
    public void testUpdateSop_success() throws Exception {
        Map<String, Object> updated = new LinkedHashMap<>();
        updated.put("id", "sop-1");
        updated.put("name", "UpdatedSOP");
        when(sopService.updateSop(eq("sop-1"), any())).thenReturn(updated);

        mockMvc
            .perform(put("/gateway/sops/sop-1").header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"UpdatedSOP\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.sop.name").value("UpdatedSOP"));
    }

    /**
     * Tests update sop not found.
     */
    @Test
    public void testUpdateSop_notFound() throws Exception {
        when(sopService.updateSop(eq("nonexistent"), any()))
            .thenThrow(new IllegalArgumentException("SOP not found: nonexistent"));

        mockMvc.perform(put("/gateway/sops/nonexistent").header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\": \"Updated\"}")).andExpect(status().isConflict());
    }

    // ── deleteSop ────────────────────────────────────────────────

    /**
     * Tests delete sop success.
     */
    @Test
    public void testDeleteSop_success() throws Exception {
        when(sopService.deleteSop("sop-1")).thenReturn(true);

        mockMvc.perform(delete("/gateway/sops/sop-1").header("x-secret-key", "test").header("x-user-id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * Tests delete sop not found.
     */
    @Test
    public void testDeleteSop_notFound() throws Exception {
        when(sopService.deleteSop("nonexistent")).thenReturn(false);

        mockMvc.perform(delete("/gateway/sops/nonexistent").header("x-secret-key", "test").header("x-user-id", "admin"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * Tests create sop duplicate name returns conflict.
     */
    @Test
    public void testCreateSop_duplicateName_returnsConflict() throws Exception {
        when(sopService.createSop(any())).thenThrow(new IllegalArgumentException("SOP name already exists: TestSOP"));

        mockMvc
            .perform(post("/gateway/sops/").header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"TestSOP\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("SOP name already exists"));
    }

    // ── Auth tests ───────────────────────────────────────────────

    /**
     * Tests list sops unauthorized no key.
     */
    @Test
    public void testListSops_unauthorized_noKey() throws Exception {
        mockMvc.perform(get("/gateway/sops/").header("x-user-id", "admin")).andExpect(status().isUnauthorized());
    }

    /**
     * Tests list sops succeeds for any authenticated user.
     */
    @Test
    public void testListSops_succeeds_forAnyUser() throws Exception {
        when(sopService.listSops()).thenReturn(List.of());

        mockMvc.perform(get("/gateway/sops/").header("x-secret-key", "test").header("x-user-id", "regular-user"))
            .andExpect(status().isOk());
    }

    /**
     * Tests create sop succeeds for any authenticated user.
     */
    @Test
    public void testCreateSop_succeeds_forAnyUser() throws Exception {
        Map<String, Object> created = new LinkedHashMap<>();
        created.put("id", "new-id");
        created.put("name", "SOP");
        when(sopService.createSop(any())).thenReturn(created);

        mockMvc.perform(post("/gateway/sops/").header("x-secret-key", "test")
            .header("x-user-id", "regular-user")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\": \"SOP\"}")).andExpect(status().isCreated());
    }
}