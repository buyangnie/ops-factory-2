/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.huawei.opsfactory.gateway.config.GatewayProperties;
import com.huawei.opsfactory.gateway.filter.AuthWebFilter;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.process.PrewarmService;
import com.huawei.opsfactory.gateway.service.SessionTraceService;
import com.huawei.opsfactory.gateway.service.SessionTraceService.TraceJobSnapshot;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
     *
     * @throws Exception test fail
     */
    @Test
    public void testStartTrace_resolvesPathVariables() throws Exception {
        when(traceService.startTrace("admin", "qa-agent", "20260429_2")).thenReturn(new TraceJobSnapshot("job-1",
            "running", "admin", "qa-agent", "20260429_2", null, "trace collection running"));

        mockMvc
            .perform(post("/api/gateway/agents/qa-agent/sessions/20260429_2/trace").header("x-secret-key", "test")
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
     *
     * @throws Exception test fail
     */
    @Test
    public void testStartTrace_succeeds_forAnyUser() throws Exception {
        when(traceService.startTrace("regular-user", "qa-agent", "20260429_2")).thenReturn(new TraceJobSnapshot("job-2",
            "running", "regular-user", "qa-agent", "20260429_2", null, "trace collection running"));

        mockMvc.perform(post("/api/gateway/agents/qa-agent/sessions/20260429_2/trace").header("x-secret-key", "test")
            .header("x-user-id", "regular-user")).andExpect(status().isOk());
    }

    /**
     * Tests download trace returns file content as byte array.
     *
     * @throws Exception test fail
     */
    @Test
    public void testDownloadTrace_returnsFileContent() throws Exception {
        Path tempFile = Files.createTempFile("trace-test", ".tar.gz");
        Files.write(tempFile, "test trace content".getBytes(StandardCharsets.UTF_8));

        when(traceService.getArchive("job-1")).thenReturn(tempFile);

        mockMvc.perform(get("/api/gateway/session-traces/job-1/download").header("x-secret-key", "test")
                .header("x-user-id", "admin")).andExpect(status().isOk())
                .andExpect(header().exists("Content-Disposition"))
                .andExpect(content().bytes("test trace content".getBytes(StandardCharsets.UTF_8)));

        verify(traceService).getArchive("job-1");
        verify(traceService).deleteJob("job-1");

        Files.deleteIfExists(tempFile);
    }
}
