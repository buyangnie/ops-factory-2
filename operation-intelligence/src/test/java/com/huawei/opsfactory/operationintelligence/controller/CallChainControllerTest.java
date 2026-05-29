/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.controller;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.huawei.opsfactory.operationintelligence.qos.model.CallChainTree;
import com.huawei.opsfactory.operationintelligence.qos.model.QueryCallChainRequest;
import com.huawei.opsfactory.operationintelligence.service.CallChainService;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

/**
 * Call Chain Controller Test.
 *
 * @author call-chain
 * @since 2026-05-18
 */
@WebMvcTest(controllers = CallChainController.class, properties = {"operation-intelligence.secret-key=test",
    "operation-intelligence.call-chain.max-time-range-ms=1800000"})
class CallChainControllerTest {

    private static final String SECRET_KEY = "test";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CallChainService callChainService;

    @Test
    void testQueryCallChainSuccess() throws Exception {
        CallChainTree tree = new CallChainTree();
        tree.setChainType("BES");
        tree.setTotalCount(100L);
        tree.setFlows(List.of());

        when(callChainService.queryCallChain(anyString(), anyList(), anyLong(), anyLong())).thenReturn(tree);

        QueryCallChainRequest request = new QueryCallChainRequest();
        request.setSolutionType("DigitalCRM.sit");
        QueryCallChainRequest.Condition condition = new QueryCallChainRequest.Condition();
        condition.setConditionKey("menuId");
        condition.setConditionValue("604015020");
        request.setCondition(List.of(condition));
        request.setStartTime(1746057600000L);
        request.setEndTime(1746058000000L);

        mockMvc
            .perform(post("/operation-intelligence/call-chain/query").header("x-secret-key", SECRET_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.chainType").value("BES"))
            .andExpect(jsonPath("$.totalCount").value(100));
    }

    @Test
    void testQueryCallChainMissingSolutionType() throws Exception {
        QueryCallChainRequest request = new QueryCallChainRequest();
        QueryCallChainRequest.Condition condition = new QueryCallChainRequest.Condition();
        condition.setConditionKey("menuId");
        condition.setConditionValue("604015020");
        request.setCondition(List.of(condition));
        request.setStartTime(1746057600000L);
        request.setEndTime(1746662400000L);

        mockMvc.perform(post("/operation-intelligence/call-chain/query").header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content(MAPPER.writeValueAsString(request))).andExpect(status().isBadRequest());
    }

    @Test
    void testQueryCallChainMissingCondition() throws Exception {
        QueryCallChainRequest request = new QueryCallChainRequest();
        request.setSolutionType("DigitalCRM.sit");
        request.setCondition(List.of());
        request.setStartTime(1746057600000L);
        request.setEndTime(1746058000000L);

        mockMvc.perform(post("/operation-intelligence/call-chain/query").header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content(MAPPER.writeValueAsString(request))).andExpect(status().isBadRequest());
    }

    @Test
    void testQueryCallChainInvalidTimeRange() throws Exception {
        QueryCallChainRequest request = new QueryCallChainRequest();
        request.setSolutionType("DigitalCRM.sit");
        QueryCallChainRequest.Condition condition = new QueryCallChainRequest.Condition();
        condition.setConditionKey("menuId");
        condition.setConditionValue("604015020");
        request.setCondition(List.of(condition));
        request.setStartTime(1746662400000L);
        request.setEndTime(1746057600000L);

        mockMvc.perform(post("/operation-intelligence/call-chain/query").header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content(MAPPER.writeValueAsString(request))).andExpect(status().isBadRequest());
    }

    @Test
    void testQueryCallChainNoAuth() throws Exception {
        QueryCallChainRequest request = new QueryCallChainRequest();
        request.setSolutionType("DigitalCRM.sit");
        QueryCallChainRequest.Condition condition = new QueryCallChainRequest.Condition();
        condition.setConditionKey("menuId");
        condition.setConditionValue("604015020");
        request.setCondition(List.of(condition));
        request.setStartTime(1746057600000L);
        request.setEndTime(1746662400000L);

        mockMvc.perform(post("/operation-intelligence/call-chain/query").contentType(MediaType.APPLICATION_JSON)
            .content(MAPPER.writeValueAsString(request))).andExpect(status().isUnauthorized());
    }
}