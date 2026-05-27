/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.huawei.opsfactory.gateway.config.GatewayProperties;
import com.huawei.opsfactory.gateway.filter.AuthWebFilter;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.process.PrewarmService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Test coverage for Status Controller.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RunWith(SpringRunner.class)
@WebMvcTest(StatusController.class)
@Import({GatewayProperties.class, AuthWebFilter.class, UserContextFilter.class})
/**
 * Status Controller Test.
 *
 * @author x00000000
 * @since 2026-05-27
 */
public class StatusControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PrewarmService prewarmService;

    /**
     * Tests status.
     */
    @Test
    public void testStatus() throws Exception {
        mockMvc.perform(get("/gateway/status").header("x-secret-key", "test"))
            .andExpect(status().isOk())
            .andExpect(content().string("ok"));
    }

    /**
     * Tests me no user id header returns unknown.
     */
    @Test
    public void testMe_noUserIdHeader_returnsUnknown() throws Exception {
        mockMvc.perform(get("/gateway/me").header("x-secret-key", "test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value("unknown"))
            .andExpect(jsonPath("$.role").value("user"));
    }

    /**
     * Tests me with user id header returns user.
     */
    @Test
    public void testMe_withUserIdHeader_returnsUser() throws Exception {
        mockMvc.perform(get("/gateway/me").header("x-secret-key", "test").header("x-user-id", "user123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value("user123"))
            .andExpect(jsonPath("$.role").value("user"));
    }

    /**
     * Tests config.
     */
    @Test
    public void testConfig() throws Exception {
        mockMvc.perform(get("/gateway/config").header("x-secret-key", "test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.officePreview.enabled").value(false));
    }

    /**
     * Tests unauthorized no key.
     */
    @Test
    public void testUnauthorized_noKey() throws Exception {
        mockMvc.perform(get("/gateway/me")).andExpect(status().isUnauthorized());
    }
}