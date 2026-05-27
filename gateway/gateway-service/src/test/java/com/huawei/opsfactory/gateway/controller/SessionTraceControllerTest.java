/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.huawei.opsfactory.gateway.config.GatewayProperties;
import com.huawei.opsfactory.gateway.filter.AuthWebFilter;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.process.PrewarmService;
import com.huawei.opsfactory.gateway.service.SessionTraceService;
import com.huawei.opsfactory.gateway.service.SessionTraceService.TraceJobSnapshot;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Test coverage for Session Trace Controller.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RunWith(SpringRunner.class)
@WebMvcTest(SessionTraceController.class)
@Import({GatewayProperties.class, AuthWebFilter.class, UserContextFilter.class})
public class SessionTraceControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SessionTraceService traceService;

    @MockBean
    private PrewarmService prewarmService;

    /**
     * Tests start trace resolves path variables.
     */
    @Test
    public void testStartTrace_resolvesPathVariables() throws Exception {
        when(traceService.startTrace("admin", "qa-agent", "20260429_2")).thenReturn(new TraceJobSnapshot("job-1",
            "running", "admin", "qa-agent", "20260429_2", null, "trace collection running"));

        mockMvc
            .perform(post("/gateway/agents/qa-agent/sessions/20260429_2/trace").header("x-secret-key", "test")
                .header("x-user-id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jobId").value("job-1"))
            .andExpect(jsonPath("$.status").value("running"))
            .andExpect(jsonPath("$.agentId").value("qa-agent"))
            .andExpect(jsonPath("$.sessionId").value("20260429_2"));

        verify(traceService).startTrace("admin", "qa-agent", "20260429_2");
    }

    /**
     * Tests start trace succeeds for any authenticated user.
     */
    @Test
    public void testStartTrace_succeeds_forAnyUser() throws Exception {
        when(traceService.startTrace("regular-user", "qa-agent", "20260429_2")).thenReturn(new TraceJobSnapshot("job-2",
            "running", "regular-user", "qa-agent", "20260429_2", null, "trace collection running"));

        mockMvc.perform(post("/gateway/agents/qa-agent/sessions/20260429_2/trace").header("x-secret-key", "test")
            .header("x-user-id", "regular-user")).andExpect(status().isOk());
    }
}
