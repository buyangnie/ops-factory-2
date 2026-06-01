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
import com.huawei.opsfactory.gateway.service.SolutionTypeService;

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
 * Test coverage for SolutionType Controller.
 *
 * @author x00000000
 * @since 2026-05-30
 */
@RunWith(SpringRunner.class)
@WebMvcTest(SolutionTypeController.class)
@Import({GatewayProperties.class, AuthWebFilter.class, UserContextFilter.class})
public class SolutionTypeControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SolutionTypeService solutionTypeService;

    @MockBean
    private com.huawei.opsfactory.gateway.process.PrewarmService prewarmService;

    // ── listSolutionTypes ──────────────────────────────────────────

    /**
     * Tests list solution types empty.
     *
     * @throws Exception test fail
     */
    @Test
    public void testListSolutionTypes_empty() throws Exception {
        when(solutionTypeService.listSolutionTypes()).thenReturn(List.of());

        mockMvc.perform(get("/gateway/solution-types/").header("x-secret-key", "test").header("x-user-id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.solutionTypes").isArray())
            .andExpect(jsonPath("$.solutionTypes").isEmpty());
    }

    /**
     * Tests list solution types with data.
     *
     * @throws Exception test fail
     */
    @Test
    public void testListSolutionTypes_withData() throws Exception {
        Map<String, Object> st = new LinkedHashMap<>();
        st.put("id", "st-1");
        st.put("name", "CRM Commerce");
        when(solutionTypeService.listSolutionTypes()).thenReturn(List.of(st));

        mockMvc.perform(get("/gateway/solution-types/").header("x-secret-key", "test").header("x-user-id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.solutionTypes[0].id").value("st-1"))
            .andExpect(jsonPath("$.solutionTypes[0].name").value("CRM Commerce"));
    }

    // ── getSolutionType ────────────────────────────────────────────

    /**
     * Tests get solution type existing.
     *
     * @throws Exception test fail
     */
    @Test
    public void testGetSolutionType_existing() throws Exception {
        Map<String, Object> st = new LinkedHashMap<>();
        st.put("id", "st-1");
        st.put("name", "CRM Commerce");
        when(solutionTypeService.getSolutionType("st-1")).thenReturn(st);

        mockMvc
            .perform(get("/gateway/solution-types/st-1").header("x-secret-key", "test").header("x-user-id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.solutionType.id").value("st-1"));
    }

    /**
     * Tests get solution type not found.
     *
     * @throws Exception test fail
     */
    @Test
    public void testGetSolutionType_notFound() throws Exception {
        when(solutionTypeService.getSolutionType("nonexistent"))
            .thenThrow(new IllegalArgumentException("Solution type not found: nonexistent"));

        mockMvc.perform(
            get("/gateway/solution-types/nonexistent").header("x-secret-key", "test").header("x-user-id", "admin"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false));
    }

    // ── createSolutionType ─────────────────────────────────────────

    /**
     * Tests create solution type success.
     *
     * @throws Exception test fail
     */
    @Test
    public void testCreateSolutionType_success() throws Exception {
        Map<String, Object> created = new LinkedHashMap<>();
        created.put("id", "new-id");
        created.put("name", "CBS Billing");
        when(solutionTypeService.createSolutionType(any())).thenReturn(created);

        mockMvc
            .perform(post("/gateway/solution-types/").header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"CBS Billing\", \"description\": \"CBS solution\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.solutionType.id").value("new-id"));
    }

    // ── updateSolutionType ─────────────────────────────────────────

    /**
     * Tests update solution type success.
     *
     * @throws Exception test fail
     */
    @Test
    public void testUpdateSolutionType_success() throws Exception {
        Map<String, Object> updated = new LinkedHashMap<>();
        updated.put("id", "st-1");
        updated.put("name", "UpdatedSolution");
        when(solutionTypeService.updateSolutionType(eq("st-1"), any())).thenReturn(updated);

        mockMvc
            .perform(put("/gateway/solution-types/st-1").header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"UpdatedSolution\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.solutionType.name").value("UpdatedSolution"));
    }

    /**
     * Tests update solution type not found.
     *
     * @throws Exception test fail
     */
    @Test
    public void testUpdateSolutionType_notFound() throws Exception {
        when(solutionTypeService.updateSolutionType(eq("nonexistent"), any()))
            .thenThrow(new IllegalArgumentException("Solution type not found: nonexistent"));

        mockMvc.perform(put("/gateway/solution-types/nonexistent").header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\": \"Updated\"}")).andExpect(status().isNotFound());
    }

    // ── deleteSolutionType ─────────────────────────────────────────

    /**
     * Tests delete solution type success.
     *
     * @throws Exception test fail
     */
    @Test
    public void testDeleteSolutionType_success() throws Exception {
        when(solutionTypeService.deleteSolutionType("st-1")).thenReturn(true);

        mockMvc
            .perform(delete("/gateway/solution-types/st-1").header("x-secret-key", "test").header("x-user-id",
                "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * Tests delete solution type not found.
     *
     * @throws Exception test fail
     */
    @Test
    public void testDeleteSolutionType_notFound() throws Exception {
        when(solutionTypeService.deleteSolutionType("nonexistent")).thenReturn(false);

        mockMvc.perform(
            delete("/gateway/solution-types/nonexistent").header("x-secret-key", "test").header("x-user-id", "admin"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false));
    }

    // ── Auth tests ───────────────────────────────────────────────

    /**
     * Tests list solution types unauthorized no key.
     *
     * @throws Exception test fail
     */
    @Test
    public void testListSolutionTypes_unauthorized_noKey() throws Exception {
        mockMvc.perform(get("/gateway/solution-types/").header("x-user-id", "admin"))
            .andExpect(status().isUnauthorized());
    }

    /**
     * Tests list solution types succeeds for any authenticated user.
     *
     * @throws Exception test fail
     */
    @Test
    public void testListSolutionTypes_succeeds_forAnyUser() throws Exception {
        when(solutionTypeService.listSolutionTypes()).thenReturn(List.of());

        mockMvc.perform(
            get("/gateway/solution-types/").header("x-secret-key", "test").header("x-user-id", "regular-user"))
            .andExpect(status().isOk());
    }
}
