/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.callchainsubgraph.service;

import com.huawei.opsfactory.operationintelligence.callchainsubgraph.model.CallChainSubgraphRequest;
import com.huawei.opsfactory.operationintelligence.callchainsubgraph.model.CallChainSubgraphResult;
import com.huawei.opsfactory.operationintelligence.callchainsubgraph.service.CallChainResourceSubgraphService.MatchResult;
import com.huawei.opsfactory.operationintelligence.callchainsubgraph.service.CallChainResourceSubgraphService.ResourceSubgraphResult;
import com.huawei.opsfactory.operationintelligence.callchainsubgraph.store.CallChainSubgraphStore;
import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.GraphEntity;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.GraphObservation;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.GraphSnapshot;
import com.huawei.opsfactory.operationintelligence.qos.model.CallChainTree;
import com.huawei.opsfactory.operationintelligence.qos.model.CallFlow;
import com.huawei.opsfactory.operationintelligence.qos.model.FlowNode;
import com.huawei.opsfactory.operationintelligence.service.CallChainService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CallChainSubgraphService}.
 */
@ExtendWith(MockitoExtension.class)
class CallChainSubgraphServiceTest {
    @Mock
    private CallChainService callChainService;

    @Mock
    private CallChainResourceSubgraphService resourceSubgraphService;

    private Path dataRoot;

    @AfterEach
    void tearDown() throws IOException {
        if (dataRoot == null || !Files.exists(dataRoot)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dataRoot)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to delete " + path, e);
                }
            });
        }
    }

    @Test
    void generate_buildsMenuEntrySubgraphAndAggregatesServiceObservations() throws IOException {
        dataRoot = Files.createTempDirectory("call-chain-subgraph-test-");
        OperationIntelligenceProperties properties = createProperties(dataRoot);
        CallChainSubgraphStore store = new CallChainSubgraphStore(properties);
        CallChainSubgraphService service =
            new CallChainSubgraphService(properties, callChainService, resourceSubgraphService, store);
        when(callChainService.queryCallChain(eq("DigitalCRM.sit"), anyList(), anyLong(), anyLong()))
            .thenReturn(createCallChainTree());
        when(resourceSubgraphService.buildResourceSubgraph(eq("b2b-callchain-v1"), eq("prod"), anyList()))
            .thenReturn(ResourceSubgraphResult.empty());

        CallChainSubgraphRequest request = new CallChainSubgraphRequest();
        request.setMenuId("6013101010007");
        request.setEnvCode("prod");
        request.setSolutionType("DigitalCRM.sit");

        CallChainSubgraphResult result = service.generate(request);

        assertNotNull(result.getSubgraphId());
        assertEquals(3, result.getGraph().getEntities().size());
        assertEquals(1, result.getGraph().getRelations().size());
        assertEquals(3, result.getGraph().getObservations().size());
        assertEquals(2, result.getSummary().get("flowCount"));
        assertEquals(0L, result.getSummary().get("resourceEntityCount"));
        assertEquals(0L, result.getSummary().get("resourceRelationCount"));
        assertTrue(result.getGraph().getRelations().stream().anyMatch(relation ->
            relation.getType().equals("belongs_to_cluster") && relation.getTo().startsWith("cc-cluster-")));
        GraphObservation avgCostObservation = result.getGraph().getObservations().stream()
            .filter(observation -> "avgCost".equals(observation.getName()))
            .findFirst()
            .orElseThrow();
        assertEquals(10L, avgCostObservation.getValue());
        assertEquals(6L, result.getGraph().getObservations().stream()
            .filter(observation -> "minCost".equals(observation.getName()))
            .findFirst()
            .orElseThrow()
            .getValue());
        assertEquals(15L, result.getGraph().getObservations().stream()
            .filter(observation -> "maxCost".equals(observation.getName()))
            .findFirst()
            .orElseThrow()
            .getValue());
        assertNotNull(service.get(result.getSubgraphId()));
    }

    @Test
    void generate_reusesMatchedResourceClusterInsteadOfSyntheticClusterEntity() throws IOException {
        dataRoot = Files.createTempDirectory("call-chain-subgraph-test-");
        OperationIntelligenceProperties properties = createProperties(dataRoot);
        CallChainSubgraphStore store = new CallChainSubgraphStore(properties);
        CallChainSubgraphService service =
            new CallChainSubgraphService(properties, callChainService, resourceSubgraphService, store);
        when(callChainService.queryCallChain(eq("DigitalCRM.sit"), anyList(), anyLong(), anyLong()))
            .thenReturn(createCallChainTree());
        when(resourceSubgraphService.buildResourceSubgraph(eq("b2b-callchain-v1"), eq("prod"), anyList()))
            .thenReturn(createBhfResourceSubgraph());

        CallChainSubgraphRequest request = new CallChainSubgraphRequest();
        request.setMenuId("6013101010007");
        request.setEnvCode("prod");
        request.setSolutionType("DigitalCRM.sit");

        CallChainSubgraphResult result = service.generate(request);

        assertTrue(result.getGraph().getEntities().stream().anyMatch(entity -> "app-bhf".equals(entity.getId())));
        assertTrue(result.getGraph().getEntities().stream().noneMatch(entity ->
            "cc-cluster-540d4035-e461-4c95-acb3-0f1f187c374d_603_bhf".equals(entity.getId())));
        assertTrue(result.getGraph().getRelations().stream().anyMatch(relation ->
            "app-bhf".equals(relation.getTo()) && "belongs_to_cluster".equals(relation.getType())));
    }

    private OperationIntelligenceProperties createProperties(Path tempRoot) {
        OperationIntelligenceProperties properties = new OperationIntelligenceProperties();
        properties.setDataRoot(tempRoot.toString());
        OperationIntelligenceProperties.KnowledgeGraph knowledgeGraph =
            new OperationIntelligenceProperties.KnowledgeGraph();
        knowledgeGraph.setEnabled(true);
        knowledgeGraph.setDataDir("knowledge-graph");
        knowledgeGraph.setCallChainSubgraphDir("call-chain-subgraphs");
        knowledgeGraph.setCallChainSubgraphRetention(10);
        knowledgeGraph.setCallChainSubgraphTtlMinutes(120);
        properties.setKnowledgeGraph(knowledgeGraph);
        OperationIntelligenceProperties.CallChain callChain = new OperationIntelligenceProperties.CallChain();
        callChain.setMaxTimeRangeMs(1800000L);
        properties.setCallChain(callChain);
        return properties;
    }

    private CallChainTree createCallChainTree() {
        CallChainTree tree = new CallChainTree();
        tree.setChainType("BES");
        tree.setFlows(List.of(
            createFlow("flow-1", 12L, 10L, 15L),
            createFlow("flow-2", 8L, 6L, 14L)));
        tree.setTotalCount(2L);
        return tree;
    }

    private CallFlow createFlow(String flowId, long avgCost, long minCost, long maxCost) {
        FlowNode entryNode = new FlowNode();
        entryNode.setSeqNo("1");
        entryNode.setUrl("https://example.com/query");
        entryNode.setAvgCost(3L);
        entryNode.setMinCost(3L);
        entryNode.setMaxCost(4L);

        FlowNode serviceNode = new FlowNode();
        serviceNode.setSeqNo("1.1");
        serviceNode.setServiceName("com.huawei.bes.sm.base.notice.SMSiteMsgUIBOService");
        serviceNode.setOperationName("queryUnreadSiteMsgCount");
        serviceNode.setClusterId("540d4035-e461-4c95-acb3-0f1f187c374d_603_BHF");
        serviceNode.setAvgCost(avgCost);
        serviceNode.setMinCost(minCost);
        serviceNode.setMaxCost(maxCost);

        CallFlow flow = new CallFlow();
        flow.setFlowId(flowId);
        flow.setCallCount(1L);
        flow.setNodes(List.of(entryNode, serviceNode));
        return flow;
    }

    private ResourceSubgraphResult createBhfResourceSubgraph() {
        GraphEntity appBhf = new GraphEntity();
        appBhf.setId("app-bhf");
        appBhf.setType("ApplicationServiceCluster");
        appBhf.setName("BHF");
        appBhf.setDisplayName("BHF");
        appBhf.setStatus("Normal");
        appBhf.setProperties(java.util.Map.of(
            "clusterId", "540d4035-e461-4c95-acb3-0f1f187c374d_603_BHF",
            "clusterName", "BHF"));

        GraphSnapshot snapshot = new GraphSnapshot();
        snapshot.setOntologyId("b2b-callchain-v1");
        snapshot.setEnvCode("prod");
        snapshot.setSchemaVersion("1.0");
        snapshot.setSourceSystem("test");
        snapshot.setImportMode("UPSERT");
        snapshot.setEntities(List.of(appBhf));
        snapshot.setRelations(List.of());
        snapshot.setObservations(List.of());
        snapshot.setMetadata(java.util.Map.of());

        return new ResourceSubgraphResult(snapshot, new MatchResult(
            java.util.Set.of("app-bhf"),
            java.util.Set.of(),
            java.util.Set.of("app-bhf"),
            java.util.Set.of()));
    }
}
