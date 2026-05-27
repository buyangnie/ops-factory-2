/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.huawei.opsfactory.gateway.common.model.AgentRegistryEntry;
import com.huawei.opsfactory.gateway.config.GatewayProperties;
import com.huawei.opsfactory.gateway.filter.AuthWebFilter;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.process.InstanceManager;
import com.huawei.opsfactory.gateway.process.PrewarmService;
import com.huawei.opsfactory.gateway.service.AgentConfigService;

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

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test coverage for Agent Controller.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RunWith(SpringRunner.class)
@WebMvcTest(AgentController.class)
@Import({GatewayProperties.class, AuthWebFilter.class, UserContextFilter.class})
public class AgentControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PrewarmService prewarmService;

    @MockBean
    private AgentConfigService agentConfigService;

    @MockBean
    private InstanceManager instanceManager;

    /**
     * Tests list agents.
     */
    @Test
    public void testListAgents() throws Exception {
        when(agentConfigService.getRegistry()).thenReturn(
            List.of(new AgentRegistryEntry("agent1", "Agent One"), new AgentRegistryEntry("agent2", "Agent Two")));
        when(agentConfigService.loadAgentConfigYaml("agent1"))
            .thenReturn(Map.of("GOOSE_PROVIDER", "openai", "GOOSE_MODEL", "gpt-4o"));
        when(agentConfigService.loadAgentConfigYaml("agent2"))
            .thenReturn(Map.of("GOOSE_PROVIDER", "anthropic", "GOOSE_MODEL", "claude-3"));
        when(agentConfigService.listSkills("agent1")).thenReturn(List
            .of(Map.of("name", "brainstorming", "description", "Brainstorm ideas", "path", "skills/brainstorming")));
        when(agentConfigService.listSkills("agent2")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/gateway/agents").header("x-secret-key", "test").header("x-user-id", "alice"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.agents[0].id").value("agent1"))
            .andExpect(jsonPath("$.agents[0].name").value("Agent One"))
            .andExpect(jsonPath("$.agents[0].sysOnly").doesNotExist())
            .andExpect(jsonPath("$.agents[0].provider").value("openai"))
            .andExpect(jsonPath("$.agents[0].skills.length()").value(1))
            .andExpect(jsonPath("$.agents[0].skills[0].name").value("brainstorming"))
            .andExpect(jsonPath("$.agents[0].skills[0].description").value("Brainstorm ideas"))
            .andExpect(jsonPath("$.agents[1].id").value("agent2"));
    }

    /**
     * Tests list agents empty.
     */
    @Test
    public void testListAgents_empty() throws Exception {
        when(agentConfigService.getRegistry()).thenReturn(List.of());

        mockMvc.perform(get("/gateway/agents").header("x-secret-key", "test").header("x-user-id", "alice"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.agents.length()").value(0));
    }

    /**
     * Tests create agent as admin.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testCreateAgent_asAdmin() throws Exception {
        Map<String, Object> agent = new HashMap<>();
        agent.put("id", "new-agent");
        agent.put("name", "New Agent");
        agent.put("provider", "openai");
        agent.put("model", "gpt-4o");
        when(agentConfigService.createAgent(eq("new-agent"), eq("New Agent"))).thenReturn(agent);

        mockMvc
            .perform(post("/gateway/agents").header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"new-agent\", \"name\": \"New Agent\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.agent.id").value("new-agent"));
    }

    /**
     * Tests create agent succeeds for any authenticated user.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testCreateAgent_succeeds_forAnyUser() throws Exception {
        Map<String, Object> agent = new HashMap<>();
        agent.put("id", "new-agent");
        agent.put("name", "New Agent");
        agent.put("provider", "openai");
        agent.put("model", "gpt-4o");
        when(agentConfigService.createAgent(eq("new-agent"), eq("New Agent"))).thenReturn(agent);

        mockMvc
            .perform(post("/gateway/agents").header("x-secret-key", "test")
                .header("x-user-id", "regular-user")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"new-agent\", \"name\": \"New Agent\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.agent.id").value("new-agent"));
    }

    /**
     * Tests create agent missing id.
     */
    @Test
    public void testCreateAgent_missingId() throws Exception {
        mockMvc.perform(post("/gateway/agents").header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\": \"New Agent\"}")).andExpect(status().isBadRequest());
    }

    /**
     * Tests delete agent as admin.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testDeleteAgent_asAdmin() throws Exception {
        Mockito.doNothing().when(instanceManager).stopAllForAgent("agent1");
        Mockito.doNothing().when(agentConfigService).deleteAgent("agent1");

        mockMvc.perform(delete("/gateway/agents/agent1").header("x-secret-key", "test").header("x-user-id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * Tests delete agent succeeds for any authenticated user.
     */
    @Test
    public void testDeleteAgent_succeeds_forAnyUser() throws Exception {
        Mockito.doNothing().when(instanceManager).stopAllForAgent("agent1");
        Mockito.doNothing().when(agentConfigService).deleteAgent("agent1");

        mockMvc
            .perform(
                delete("/gateway/agents/agent1").header("x-secret-key", "test").header("x-user-id", "regular-user"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * Tests get skills as admin.
     */
    @Test
    public void testGetSkills_asAdmin() throws Exception {
        when(agentConfigService.listSkills("agent1")).thenReturn(
            List.of(Map.of("name", "brainstorming", "description", "Brainstorm ideas", "path", "skills/brainstorming"),
                Map.of("name", "analysis", "description", "Analyze data", "path", "skills/analysis")));

        mockMvc
            .perform(get("/gateway/agents/agent1/skills").header("x-secret-key", "test").header("x-user-id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.skills[0].name").value("brainstorming"))
            .andExpect(jsonPath("$.skills[0].description").value("Brainstorm ideas"))
            .andExpect(jsonPath("$.skills[1].name").value("analysis"))
            .andExpect(jsonPath("$.skills[1].description").value("Analyze data"));
    }

    /**
     * Tests get config as admin.
     */
    @Test
    public void testGetConfig_asAdmin() throws Exception {
        when(agentConfigService.findAgent("agent1")).thenReturn(new AgentRegistryEntry("agent1", "Agent One"));
        when(agentConfigService.readAgentsMd("agent1")).thenReturn("# Agent One\n");
        Map<String, Object> config = new HashMap<>();
        config.put("GOOSE_PROVIDER", "anthropic");
        config.put("GOOSE_MODEL", "claude-3");
        when(agentConfigService.loadAgentConfigYaml("agent1")).thenReturn(config);
        when(agentConfigService.getAgentsDir()).thenReturn(Path.of("/tmp/agents"));

        mockMvc
            .perform(get("/gateway/agents/agent1/config").header("x-secret-key", "test").header("x-user-id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.agentsMd").value("# Agent One\n"))
            .andExpect(jsonPath("$.provider").value("anthropic"))
            .andExpect(jsonPath("$.model").value("claude-3"));
    }

    /**
     * Tests update config as admin.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testUpdateConfig_asAdmin() throws Exception {
        when(agentConfigService.findAgent("agent1")).thenReturn(new AgentRegistryEntry("agent1", "Agent One"));
        Mockito.doNothing().when(agentConfigService).writeAgentsMd("agent1", "# Updated\n");

        mockMvc
            .perform(put("/gateway/agents/agent1/config").header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"agentsMd\": \"# Updated\\n\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * Tests update config succeeds for any authenticated user.
     */
    @Test
    public void testUpdateConfig_succeeds_forAnyUser() throws Exception {
        when(agentConfigService.findAgent("agent1")).thenReturn(new AgentRegistryEntry("agent1", "Agent One"));
        Mockito.doNothing().when(agentConfigService).writeAgentsMd("agent1", "# Updated\n");

        mockMvc
            .perform(put("/gateway/agents/agent1/config").header("x-secret-key", "test")
                .header("x-user-id", "regular-user")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"agentsMd\": \"# Updated\\n\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * Tests create agent missing name.
     */
    @Test
    public void testCreateAgent_missingName() throws Exception {
        mockMvc.perform(post("/gateway/agents").header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\": \"new-agent\"}")).andExpect(status().isBadRequest());
    }

    /**
     * Tests create agent blank id.
     */
    @Test
    public void testCreateAgent_blankId() throws Exception {
        mockMvc.perform(post("/gateway/agents").header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\": \"   \", \"name\": \"New Agent\"}")).andExpect(status().isBadRequest());
    }

    /**
     * Tests create agent duplicate returns400.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testCreateAgent_duplicateReturns400() throws Exception {
        when(agentConfigService.createAgent(eq("dup-agent"), eq("Dup Agent")))
            .thenThrow(new IllegalArgumentException("Agent already exists"));

        mockMvc.perform(post("/gateway/agents").header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\": \"dup-agent\", \"name\": \"Dup Agent\"}")).andExpect(status().isBadRequest());
    }

    /**
     * Tests create agent io failure returns500.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testCreateAgent_ioFailureReturns500() throws Exception {
        when(agentConfigService.createAgent(eq("io-agent"), eq("IO Agent")))
            .thenThrow(new IllegalStateException("disk full"));

        mockMvc
            .perform(post("/gateway/agents").header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"io-agent\", \"name\": \"IO Agent\"}"))
            .andExpect(status().is5xxServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Failed to create agent"));
    }

    /**
     * Tests get skills succeeds for any authenticated user.
     */
    @Test
    public void testGetSkills_succeeds_forAnyUser() throws Exception {
        when(agentConfigService.listSkills("agent1")).thenReturn(
            List.of(Map.of("name", "brainstorming", "description", "Brainstorm ideas", "path", "skills/brainstorming"),
                Map.of("name", "analysis", "description", "Analyze data", "path", "skills/analysis")));

        mockMvc
            .perform(
                get("/gateway/agents/agent1/skills").header("x-secret-key", "test").header("x-user-id", "regular-user"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.skills[0].name").value("brainstorming"))
            .andExpect(jsonPath("$.skills[0].description").value("Brainstorm ideas"))
            .andExpect(jsonPath("$.skills[1].name").value("analysis"))
            .andExpect(jsonPath("$.skills[1].description").value("Analyze data"));
    }

    /**
     * Tests get config succeeds for any authenticated user.
     */
    @Test
    public void testGetConfig_succeeds_forAnyUser() throws Exception {
        when(agentConfigService.findAgent("agent1")).thenReturn(new AgentRegistryEntry("agent1", "Agent One"));
        when(agentConfigService.readAgentsMd("agent1")).thenReturn("# Agent One\n");
        Map<String, Object> config = new HashMap<>();
        config.put("GOOSE_PROVIDER", "anthropic");
        config.put("GOOSE_MODEL", "claude-3");
        when(agentConfigService.loadAgentConfigYaml("agent1")).thenReturn(config);
        when(agentConfigService.getAgentsDir()).thenReturn(Path.of("/tmp/agents"));

        mockMvc
            .perform(
                get("/gateway/agents/agent1/config").header("x-secret-key", "test").header("x-user-id", "regular-user"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.agentsMd").value("# Agent One\n"))
            .andExpect(jsonPath("$.provider").value("anthropic"))
            .andExpect(jsonPath("$.model").value("claude-3"));
    }

    /**
     * Tests list agents no auth required.
     */
    @Test
    public void testListAgents_noAuthRequired() throws Exception {
        // listAgents does not require admin, just auth
        when(agentConfigService.getRegistry()).thenReturn(List.of());

        mockMvc.perform(get("/gateway/agents").header("x-secret-key", "test").header("x-user-id", "regular-user"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.agents").isArray());
    }
}
