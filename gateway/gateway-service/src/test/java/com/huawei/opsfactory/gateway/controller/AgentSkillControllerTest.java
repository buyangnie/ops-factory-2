/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.huawei.opsfactory.gateway.config.GatewayProperties;
import com.huawei.opsfactory.gateway.filter.AuthWebFilter;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.process.PrewarmService;
import com.huawei.opsfactory.gateway.service.AgentSkillInstallService;
import com.huawei.opsfactory.gateway.service.SkillInstallConflictException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

/**
 * Test coverage for Agent Skill Controller.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RunWith(SpringRunner.class)
@WebMvcTest(AgentSkillController.class)
@Import({GatewayProperties.class, AuthWebFilter.class, UserContextFilter.class})
/**
 * Agent Skill Controller Test.
 *
 * @author x00000000
 * @since 2026-05-27
 */
public class AgentSkillControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PrewarmService prewarmService;

    @MockBean
    private AgentSkillInstallService installService;

    /**
     * Executes the install skill as admin operation.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void installSkill_asAdmin() throws Exception {
        Mockito.when(installService.install("agent1", "log-analysis"))
            .thenReturn(Map.of("success", true, "skill", Map.of("id", "log-analysis"), "restartRequired", true));

        mockMvc
            .perform(post("/gateway/agents/agent1/skills/install").header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"skillId\":\"log-analysis\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.skill.id").value("log-analysis"))
            .andExpect(jsonPath("$.restartRequired").value(true));
    }

    /**
     * Executes the install skill succeeds for any authenticated user.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void installSkill_succeeds_forAnyUser() throws Exception {
        Mockito.when(installService.install("agent1", "log-analysis"))
            .thenReturn(Map.of("success", true, "skill", Map.of("id", "log-analysis"), "restartRequired", true));

        mockMvc
            .perform(post("/gateway/agents/agent1/skills/install").header("x-secret-key", "test")
                .header("x-user-id", "regular-user")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"skillId\":\"log-analysis\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.skill.id").value("log-analysis"))
            .andExpect(jsonPath("$.restartRequired").value(true));
    }

    /**
     * Executes the install skill conflict operation.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void installSkill_conflict() throws Exception {
        Mockito.when(installService.install("agent1", "log-analysis"))
            .thenThrow(new SkillInstallConflictException("Skill already installed"));

        mockMvc
            .perform(post("/gateway/agents/agent1/skills/install").header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"skillId\":\"log-analysis\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * Executes the uninstall skill as admin operation.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void uninstallSkill_asAdmin() throws Exception {
        Mockito.when(installService.uninstall("agent1", "log-analysis"))
            .thenReturn(Map.of("success", true, "skillId", "log-analysis", "restartRequired", true));

        mockMvc
            .perform(delete("/gateway/agents/agent1/skills/log-analysis").header("x-secret-key", "test")
                .header("x-user-id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.skillId").value("log-analysis"))
            .andExpect(jsonPath("$.restartRequired").value(true));
    }

    /**
     * Executes the uninstall skill succeeds for any authenticated user.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void uninstallSkill_succeeds_forAnyUser() throws Exception {
        Mockito.when(installService.uninstall("agent1", "log-analysis"))
            .thenReturn(Map.of("success", true, "skillId", "log-analysis", "restartRequired", true));

        mockMvc
            .perform(delete("/gateway/agents/agent1/skills/log-analysis").header("x-secret-key", "test")
                .header("x-user-id", "regular-user"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.skillId").value("log-analysis"))
            .andExpect(jsonPath("$.restartRequired").value(true));
    }
}