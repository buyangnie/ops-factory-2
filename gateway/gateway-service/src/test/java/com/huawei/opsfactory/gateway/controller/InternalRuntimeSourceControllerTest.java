/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.huawei.opsfactory.gateway.common.model.ManagedInstance;
import com.huawei.opsfactory.gateway.config.GatewayProperties;
import com.huawei.opsfactory.gateway.filter.AuthWebFilter;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.monitoring.MetricsBuffer;
import com.huawei.opsfactory.gateway.monitoring.MetricsSnapshot;
import com.huawei.opsfactory.gateway.process.InstanceManager;
import com.huawei.opsfactory.gateway.process.PrewarmService;
import com.huawei.opsfactory.gateway.service.AgentConfigService;
import com.huawei.opsfactory.gateway.service.LangfuseService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

/**
 * Test coverage for Internal Runtime Source Controller.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RunWith(SpringRunner.class)
@WebMvcTest(InternalRuntimeSourceController.class)
@Import({GatewayProperties.class, AuthWebFilter.class, UserContextFilter.class})
/**
 * Internal Runtime Source Controller Test.
 *
 * @author x00000000
 * @since 2026-05-27
 */
public class InternalRuntimeSourceControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PrewarmService prewarmService;

    @MockBean
    private InstanceManager instanceManager;

    @MockBean
    private AgentConfigService agentConfigService;

    @MockBean
    private LangfuseService langfuseService;

    @MockBean
    private MetricsBuffer metricsBuffer;

    /**
     * Tests system as admin.
     */
    @Test
    public void testSystem_asAdmin() throws Exception {
        when(agentConfigService.getRegistry()).thenReturn(List.of());
        when(instanceManager.getAllInstances()).thenReturn(List.of());
        when(langfuseService.isConfigured()).thenReturn(false);

        mockMvc
            .perform(get("/gateway/runtime-source/system").header("x-secret-key", "test").header("x-user-id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.gateway.uptimeMs").exists())
            .andExpect(jsonPath("$.gateway.host").exists())
            .andExpect(jsonPath("$.gateway.port").exists())
            .andExpect(jsonPath("$.agents.configured").value(0))
            .andExpect(jsonPath("$.idle.timeoutMs").exists())
            .andExpect(jsonPath("$.langfuse.configured").value(false));
    }

    /**
     * Tests system succeeds for any authenticated user.
     */
    @Test
    public void testSystem_succeeds_forAnyUser() throws Exception {
        when(agentConfigService.getRegistry()).thenReturn(List.of());
        when(instanceManager.getAllInstances()).thenReturn(List.of());
        when(langfuseService.isConfigured()).thenReturn(false);

        mockMvc
            .perform(get("/gateway/runtime-source/system").header("x-secret-key", "test")
                .header("x-user-id", "regular-user"))
            .andExpect(status().isOk());
    }

    /**
     * Tests instances.
     */
    @Test
    public void testInstances() throws Exception {
        ManagedInstance inst = new ManagedInstance("agent1", "user1", 9090, 5678L, null, "test-secret");
        inst.setStatus(ManagedInstance.Status.RUNNING);
        when(instanceManager.getAllInstances()).thenReturn(List.of(inst));
        when(agentConfigService.findAgent("agent1"))
            .thenReturn(new com.huawei.opsfactory.gateway.common.model.AgentRegistryEntry("agent1", "Agent One"));

        mockMvc
            .perform(
                get("/gateway/runtime-source/instances").header("x-secret-key", "test").header("x-user-id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalInstances").value(1))
            .andExpect(jsonPath("$.runningInstances").value(1))
            .andExpect(jsonPath("$.byAgent[0].agentId").value("agent1"))
            .andExpect(jsonPath("$.byAgent[0].agentName").value("Agent One"))
            .andExpect(jsonPath("$.byAgent[0].instances[0].userId").value("user1"))
            .andExpect(jsonPath("$.byAgent[0].instances[0].port").value(9090))
            .andExpect(jsonPath("$.byAgent[0].instances[0].status").value("running"))
            .andExpect(jsonPath("$.byAgent[0].instances[0].idleSinceMs").exists());
    }

    /**
     * Tests instances succeeds for any authenticated user.
     */
    @Test
    public void testInstances_succeeds_forAnyUser() throws Exception {
        when(instanceManager.getAllInstances()).thenReturn(List.of());

        mockMvc
            .perform(get("/gateway/runtime-source/instances").header("x-secret-key", "test")
                .header("x-user-id", "regular-user"))
            .andExpect(status().isOk());
    }

    /**
     * Tests metrics empty.
     */
    @Test
    public void testMetrics_empty() throws Exception {
        when(metricsBuffer.getSnapshots(120)).thenReturn(List.of());

        mockMvc
            .perform(get("/gateway/runtime-source/metrics").header("x-secret-key", "test").header("x-user-id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.collectionIntervalSec").value(30))
            .andExpect(jsonPath("$.maxSlots").value(120))
            .andExpect(jsonPath("$.returnedSlots").value(0))
            .andExpect(jsonPath("$.current").exists())
            .andExpect(jsonPath("$.aggregate.totalRequests").value(0))
            .andExpect(jsonPath("$.aggregate.totalErrors").value(0))
            .andExpect(jsonPath("$.series.length()").value(0));
    }

    /**
     * Tests metrics with snapshots.
     */
    @Test
    public void testMetrics_withSnapshots() throws Exception {
        MetricsSnapshot s1 = new MetricsSnapshot();
        s1.setTimestamp(1000L);
        s1.setActiveInstances(2);
        s1.setTotalTokens(5000);
        s1.setTotalSessions(3);
        s1.setRequestCount(4);
        s1.setAvgLatencyMs(2000.0);
        s1.setAvgTtftMs(500.0);
        s1.setP95LatencyMs(3000.0);
        s1.setP95TtftMs(800.0);
        s1.setTotalBytes(10000);
        s1.setErrorCount(1);

        MetricsSnapshot s2 = new MetricsSnapshot();
        s2.setTimestamp(2000L);
        s2.setActiveInstances(3);
        s2.setTotalTokens(8000);
        s2.setTotalSessions(5);
        s2.setRequestCount(6);
        s2.setAvgLatencyMs(3000.0);
        s2.setAvgTtftMs(700.0);
        s2.setP95LatencyMs(5000.0);
        s2.setP95TtftMs(1200.0);
        s2.setTotalBytes(20000);
        s2.setErrorCount(0);

        when(metricsBuffer.getSnapshots(120)).thenReturn(List.of(s1, s2));
        when(metricsBuffer.getAgentStats()).thenReturn(Map.of());

        mockMvc
            .perform(get("/gateway/runtime-source/metrics").header("x-secret-key", "test").header("x-user-id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.returnedSlots").value(2))
            .andExpect(jsonPath("$.current.activeInstances").value(3))
            .andExpect(jsonPath("$.current.totalTokens").value(8000))
            .andExpect(jsonPath("$.current.totalSessions").value(5))
            .andExpect(jsonPath("$.aggregate.totalRequests").value(10))
            .andExpect(jsonPath("$.aggregate.totalErrors").value(1))
            .andExpect(jsonPath("$.aggregate.avgLatencyMs").value(2600.0))
            .andExpect(jsonPath("$.series.length()").value(2))
            .andExpect(jsonPath("$.series[0].t").value(1000))
            .andExpect(jsonPath("$.series[1].t").value(2000));
    }

    /**
     * Tests metrics succeeds for any authenticated user.
     */
    @Test
    public void testMetrics_succeeds_forAnyUser() throws Exception {
        when(metricsBuffer.getSnapshots(120)).thenReturn(List.of());

        mockMvc
            .perform(get("/gateway/runtime-source/metrics").header("x-secret-key", "test")
                .header("x-user-id", "regular-user"))
            .andExpect(status().isOk());
    }
}