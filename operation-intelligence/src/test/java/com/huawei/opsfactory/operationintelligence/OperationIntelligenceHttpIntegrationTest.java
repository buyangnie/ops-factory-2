/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Operation Intelligence HTTP Integration Test.
 *
 * @author x00000000
 * @since 2026-05-11
 */
@SpringBootTest
@AutoConfigureMockMvc
class OperationIntelligenceHttpIntegrationTest {

    private static final String SECRET_KEY = "test";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void actuatorHealth_isReachableWithoutSecret() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"status\"")));
    }

    @Test
    void qosEndpoints_requireSecret() throws Exception {
        mockMvc.perform(get("/operation-intelligence/qos/getEnvironments"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void qosEndpoints_withValidSecret_success() throws Exception {
        mockMvc.perform(get("/operation-intelligence/qos/getEnvironments")
                .header("x-secret-key", SECRET_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results").isArray());
    }

    @Test
    void qosEndpoints_withQueryParameterKey_success() throws Exception {
        mockMvc.perform(get("/operation-intelligence/qos/getEnvironments?key=" + SECRET_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results").isArray());
    }

    @Test
    void cors_preflight_requiresSecret() throws Exception {
        // CORS preflight requests should bypass auth for OPTIONS
        mockMvc.perform(post("/operation-intelligence/qos/getEnvironments")
                .header("Access-Control-Request-Method", "GET")
                .header("Origin", "http://localhost:3000"))
            .andExpect(status().isUnauthorized());
    }
}
