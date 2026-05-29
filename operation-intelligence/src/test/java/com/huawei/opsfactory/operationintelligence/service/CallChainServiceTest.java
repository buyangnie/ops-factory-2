/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;
import com.huawei.opsfactory.operationintelligence.qos.dv.DvClient;
import com.huawei.opsfactory.operationintelligence.qos.model.CallChainTree;
import com.huawei.opsfactory.operationintelligence.qos.model.ChainTypeConfig;
import com.huawei.opsfactory.operationintelligence.qos.model.TraceLogRecord;
import com.huawei.opsfactory.operationintelligence.qos.parser.TimeSplitStrategy;
import com.huawei.opsfactory.operationintelligence.qos.store.CallChainStore;
import com.huawei.opsfactory.operationintelligence.qos.store.ChainTypeConfigStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Call Chain Service Test.
 *
 * @author call-chain
 * @since 2026-05-18
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CallChainServiceTest {

    @Mock
    private OperationIntelligenceProperties properties;

    @Mock
    private DvClient dvClient;

    @Mock
    private CallChainBuilder chainBuilder;

    @Mock
    private CallChainStore chainStore;

    @Mock
    private ChainTypeConfigStore configStore;

    @Mock
    private TimeSplitStrategy timeSplitStrategy;

    private CallChainService callChainService;

    @BeforeEach
    void setUp() {
        callChainService =
            new CallChainService(properties, dvClient, chainBuilder, chainStore, configStore, timeSplitStrategy);
    }

    @Test
    void testQueryCallChainSuccess() {
        OperationIntelligenceProperties.CallChain callChain = new OperationIntelligenceProperties.CallChain();
        callChain.setQuerySize(100);
        when(properties.getCallChain()).thenReturn(callChain);
        when(configStore.loadAll()).thenReturn(List.of());
        when(dvClient.fetchTraceLogEntries(anyString(), anyString(), anyString(), anyList(), any(), anyLong(),
            anyLong(), anyInt())).thenReturn(List.of());

        CallChainTree tree = new CallChainTree();
        tree.setChainType(null);
        tree.setFlows(new ArrayList<>());
        tree.setTotalCount(0L);
        when(chainBuilder.build(anyString(), anyString(), anyString(), anyList(), anyLong())).thenReturn(tree);

        CallChainTree result = callChainService.queryCallChain("DigitalCRM.sit",
            List.of(Map.of("conditionKey", "menuId", "conditionValue", "604015020")), 1746057600000L, 1746662400000L);

        assertNotNull(result);
        assertNull(result.getChainType());
        assertEquals(0L, result.getTotalCount());
    }

    @Test
    void testQueryCallChainWithValidTraceLogs() {
        OperationIntelligenceProperties.CallChain callChain = new OperationIntelligenceProperties.CallChain();
        callChain.setQuerySize(100);
        callChain.setQueryLimit(10000);
        when(properties.getCallChain()).thenReturn(callChain);

        // Configure chain type config
        ChainTypeConfig config = new ChainTypeConfig();
        config.setChainType("BES");
        config.setConditionKey("menuId");
        when(configStore.loadAll()).thenReturn(List.of(config));
        when(configStore.getByChainType("BES")).thenReturn(config);

        // Mock entry logs (seqNo=1) with trace IDs
        TraceLogRecord entryLog = new TraceLogRecord();
        entryLog.setTraceId("trace-123");
        entryLog.setSeqNo("1");
        when(dvClient.fetchTraceLogEntries(anyString(), anyString(), anyString(), anyList(), any(), anyLong(),
            anyLong(), anyInt())).thenReturn(List.of(entryLog));

        // Mock complete trace logs for the trace ID
        TraceLogRecord fullLog = new TraceLogRecord();
        fullLog.setTraceId("trace-123");
        fullLog.setSeqNo("1");
        fullLog.setIp("10.0.0.1");
        when(dvClient.fetchByTraceId(anyString(), eq("trace-123"), anyLong(), anyLong(), anyInt()))
            .thenReturn(List.of(fullLog));

        // Mock chain builder to return a new tree for debugging
        when(chainBuilder.build(anyString(), anyString(), anyString(), anyList(), anyLong())).thenAnswer(invocation -> {
            CallChainTree result = new CallChainTree();
            result.setChainType("BES");
            result.setFlows(List.of());
            result.setTotalCount(1L);
            return result;
        });

        CallChainTree result = callChainService.queryCallChain("DigitalCRM.sit",
            List.of(Map.of("conditionKey", "menuId", "conditionValue", "604015020")), 1746057600000L, 1746662400000L);

        assertNotNull(result);
        assertEquals("BES", result.getChainType());
        assertEquals(1L, result.getTotalCount());
    }

    @Test
    void testQueryCallChainWithEmptyConditions() {
        OperationIntelligenceProperties.CallChain callChain = new OperationIntelligenceProperties.CallChain();
        callChain.setQuerySize(100);
        when(properties.getCallChain()).thenReturn(callChain);

        CallChainTree result =
            callChainService.queryCallChain("DigitalCRM.sit", List.of(), 1746057600000L, 1746662400000L);

        assertNotNull(result);
    }

    @Test
    void testQueryCallChainWithNullConditions() {
        OperationIntelligenceProperties.CallChain callChain = new OperationIntelligenceProperties.CallChain();
        callChain.setQuerySize(100);
        when(properties.getCallChain()).thenReturn(callChain);

        CallChainTree result =
            callChainService.queryCallChain("DigitalCRM.sit", new ArrayList<>(), 1746057600000L, 1746662400000L);

        assertNotNull(result);
        assertNull(result.getChainType());
    }
}