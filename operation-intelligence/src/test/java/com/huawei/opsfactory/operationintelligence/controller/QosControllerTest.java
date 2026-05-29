/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.huawei.opsfactory.operationintelligence.qos.model.ProductConfigRule;
import com.huawei.opsfactory.operationintelligence.service.QosService;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Qos Controller Test.
 *
 * @author x00000000
 * @since 2026-05-11
 */
@WebMvcTest(controllers = QosController.class, properties = {"operation-intelligence.secret-key=test"})
class QosControllerTest {

    private static final String SECRET_KEY = "test";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QosService qosService;

    @Test
    void testGetEnvironments() throws Exception {
        List<Map<String, String>> envs = List.of(Map.of("envCode", "env1", "envName", "Environment 1"),
            Map.of("envCode", "env2", "envName", "Environment 2"));
        when(qosService.getEnvironments()).thenReturn(envs);

        mockMvc.perform(get("/operation-intelligence/qos/getEnvironments").header("x-secret-key", SECRET_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results").isArray())
            .andExpect(jsonPath("$.results.length()").value(2))
            .andExpect(jsonPath("$.results[0].envCode").value("env1"));
    }

    @Test
    void testGetEnvironmentsNoAuth() throws Exception {
        mockMvc.perform(get("/operation-intelligence/qos/getEnvironments")).andExpect(status().isUnauthorized());
    }

    @Test
    void testGetHealthIndicator() throws Exception {
        when(qosService.getHealthIndicator(anyString(), anyLong(), anyLong()))
            .thenReturn(List.of(Map.of("timestamp", 1234567890L, "availability", 0.95)));

        Map<String, Object> req = Map.of("envCode", "test-env", "startTime", 1746057600000L, "endTime", 1746058000000L);

        mockMvc
            .perform(post("/operation-intelligence/qos/getHealthIndicator").header("x-secret-key", SECRET_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results").isArray())
            .andExpect(jsonPath("$.results.length()").value(1));
    }

    @Test
    void testGetHealthIndicatorMissingEnvCode() throws Exception {
        Map<String, Object> req = Map.of("startTime", 1746057600000L, "endTime", 1746058000000L);

        mockMvc.perform(post("/operation-intelligence/qos/getHealthIndicator").header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content(MAPPER.writeValueAsString(req))).andExpect(status().isBadRequest());
    }

    @Test
    void testGetProductConfigRule() throws Exception {
        ProductConfigRule rule = new ProductConfigRule();
        rule.setAgentSolutionType("DigitalCRM");
        when(qosService.getProductConfigRule(anyString())).thenReturn(Optional.<ProductConfigRule>of(rule));

        Map<String, Object> req = Map.of("agentSolutionType", "DigitalCRM");

        mockMvc
            .perform(post("/operation-intelligence/qos/getProductConfigRule").header("x-secret-key", SECRET_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.agentSolutionType").value("DigitalCRM"));
    }

    @Test
    void testGetProductConfigRuleNotFound() throws Exception {
        when(qosService.getProductConfigRule(anyString())).thenReturn(Optional.<ProductConfigRule>empty());

        Map<String, Object> req = Map.of("agentSolutionType", "NonExistent");

        mockMvc.perform(post("/operation-intelligence/qos/getProductConfigRule").header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content(MAPPER.writeValueAsString(req))).andExpect(status().isNotFound());
    }
}