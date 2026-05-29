/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.huawei.opsfactory.operationintelligence.qos.model.CallChainTree;
import com.huawei.opsfactory.operationintelligence.qos.model.CallFlow;
import com.huawei.opsfactory.operationintelligence.qos.model.FlowNode;
import com.huawei.opsfactory.operationintelligence.service.CallChainService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Integration tests for call chain subgraph REST API endpoints.
 *
 * @author x00000000
 * @since 2026-05-28
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CallChainSubgraphControllerTest {
    private static final String SECRET_KEY = "cc-subgraph-secret";

    private static final Path DATA_ROOT = createTempDataRoot();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CallChainService callChainService;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("operation-intelligence.secret-key", () -> SECRET_KEY);
        registry.add("operation-intelligence.data-root", DATA_ROOT::toString);
        registry.add("operation-intelligence.qos.enabled", () -> "false");
        registry.add("operation-intelligence.knowledge-graph.enabled", () -> "true");
        registry.add("operation-intelligence.knowledge-graph.call-chain-subgraph-retention", () -> "10");
        registry.add("operation-intelligence.knowledge-graph.call-chain-subgraph-ttl-minutes", () -> "120");
        registry.add("operation-intelligence.call-chain.max-time-range-ms", () -> "1800000");
    }

    @AfterAll
    static void tearDown() throws IOException {
        deleteRecursively(DATA_ROOT);
    }

    @BeforeEach
    void setUp() throws IOException {
        deleteRecursively(DATA_ROOT);
        Files.createDirectories(DATA_ROOT);
    }

    @Test
    void generateAndGetSubgraph_persistsStoredSnapshot() throws Exception {
        when(callChainService.queryCallChain(eq("DigitalCRM.sit"), anyList(), anyLong(), anyLong()))
            .thenReturn(createCallChainTree());

        Map<String, Object> request = Map.of(
            "menuId", "6013101010007",
            "envCode", "prod",
            "solutionType", "DigitalCRM.sit",
            "ontologyId", "b2b-callchain-v1",
            "startTime", 1746057600000L,
            "endTime", 1746058200000L);

        MvcResult generateResult = mockMvc.perform(post("/operation-intelligence/call-chain/subgraphs")
                .header("x-secret-key", SECRET_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.result.menuId").value("6013101010007"))
            .andExpect(jsonPath("$.result.summary.flowCount").value(1))
            .andExpect(jsonPath("$.result.graph.entities.length()").value(3))
            .andExpect(jsonPath("$.result.graph.relations.length()").value(1))
            .andExpect(jsonPath("$.result.graph.observations.length()").value(3))
            .andReturn();

        JsonNode generateBody = MAPPER.readTree(generateResult.getResponse().getContentAsString());
        String subgraphId = generateBody.path("result").path("subgraphId").asText();

        assertTrue(subgraphId.startsWith("cc-subgraph-"));
        assertEquals(1L, countPersistedSubgraphs());

        mockMvc.perform(get("/operation-intelligence/call-chain/subgraphs/{subgraphId}", subgraphId)
                .header("x-secret-key", SECRET_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.result.subgraphId").value(subgraphId))
            .andExpect(jsonPath("$.result.graph.metadata.entryType").value("menuId"))
            .andExpect(jsonPath("$.result.graph.metadata.flowCount").value(1))
            .andExpect(jsonPath("$.result.graph.relations[0].type").value("belongs_to_cluster"));

        mockMvc.perform(get("/operation-intelligence/call-chain/subgraphs")
                .header("x-secret-key", SECRET_KEY)
                .queryParam("ontologyId", "b2b-callchain-v1")
                .queryParam("envCode", "prod"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.result.length()").value(1))
            .andExpect(jsonPath("$.result[0].subgraphId").value(subgraphId))
            .andExpect(jsonPath("$.result[0].menuId").value("6013101010007"));
    }

    @Test
    void generateSubgraph_requiresSecretKey() throws Exception {
        Map<String, Object> request = Map.of(
            "menuId", "6013101010007",
            "envCode", "prod",
            "solutionType", "DigitalCRM.sit");

        mockMvc.perform(post("/operation-intelligence/call-chain/subgraphs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(MAPPER.writeValueAsString(request))).andExpect(status().isUnauthorized());
    }

    private static Path createTempDataRoot() {
        try {
            return Files.createTempDirectory("operation-intelligence-call-chain-subgraph-test-");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create test data root", e);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to delete " + path, e);
                }
            });
        }
    }

    private long countPersistedSubgraphs() throws IOException {
        Path subgraphRoot = DATA_ROOT.resolve("knowledge-graph").resolve("call-chain-subgraphs");
        if (!Files.isDirectory(subgraphRoot)) {
            return 0L;
        }
        try (Stream<Path> paths = Files.list(subgraphRoot)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".json")).count();
        }
    }

    private CallChainTree createCallChainTree() {
        CallChainTree tree = new CallChainTree();
        tree.setChainType("BES");
        tree.setFlows(List.of(createFlow()));
        tree.setTotalCount(1L);
        return tree;
    }

    private CallFlow createFlow() {
        FlowNode entryNode = new FlowNode();
        entryNode.setSeqNo("1");
        entryNode.setUrl("https://example.com/query");

        FlowNode serviceNode = new FlowNode();
        serviceNode.setSeqNo("1.1");
        serviceNode.setServiceName("com.huawei.bes.sm.base.notice.SMSiteMsgUIBOService");
        serviceNode.setOperationName("queryUnreadSiteMsgCount");
        serviceNode.setClusterId("540d4035-e461-4c95-acb3-0f1f187c374d_603_BHF");
        serviceNode.setAvgCost(12L);
        serviceNode.setMinCost(10L);
        serviceNode.setMaxCost(15L);

        CallFlow flow = new CallFlow();
        flow.setFlowId("flow-1");
        flow.setCallCount(1L);
        flow.setNodes(List.of(entryNode, serviceNode));
        return flow;
    }
}
