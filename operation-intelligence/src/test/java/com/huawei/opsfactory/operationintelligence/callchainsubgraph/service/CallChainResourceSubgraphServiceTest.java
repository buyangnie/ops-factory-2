/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.callchainsubgraph.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.GraphEntity;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.GraphRelation;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.GraphSnapshot;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.service.GraphSchemaRegistry;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.service.KnowledgeGraphService;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.store.GraphOntologyStore;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.store.GraphSnapshotStore;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.store.InMemoryGraphStore;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for {@link CallChainResourceSubgraphService}.
 */
class CallChainResourceSubgraphServiceTest {
    @Test
    void buildResourceSubgraph_extractsClusterPodAndNodeTopology() throws Exception {
        OperationIntelligenceProperties properties = createProperties(Files.createTempDirectory("resource-subgraph-"));
        InMemoryGraphStore graphStore = new InMemoryGraphStore();
        GraphSnapshot envSnapshot = createEnvironmentSnapshot();
        graphStore.loadSnapshot(envSnapshot);
        KnowledgeGraphService knowledgeGraphService = new KnowledgeGraphService(
            properties,
            new GraphSchemaRegistry(),
            graphStore,
            new GraphOntologyStore(properties),
            new GraphSnapshotStore(properties));
        CallChainResourceSubgraphService service = new CallChainResourceSubgraphService(properties, knowledgeGraphService);

        GraphEntity callChainServiceEntity = new GraphEntity();
        callChainServiceEntity.setId("cc-svc-sms");
        callChainServiceEntity.setType("Service");
        callChainServiceEntity.setName("com.huawei.bes.sm.base.notice.SMSiteMsgUIBOService");
        callChainServiceEntity.setProperties(new LinkedHashMap<>(Map.of(
            "serviceName", "com.huawei.bes.sm.base.notice.SMSiteMsgUIBOService",
            "clusterId", "540d4035-e461-4c95-acb3-0f1f187c374d_603_BHF")));

        CallChainResourceSubgraphService.ResourceSubgraphResult result = service.buildResourceSubgraph(
            "b2b-callchain-v1",
            "prod",
            List.of(callChainServiceEntity));

        assertEquals(4, result.graph().getEntities().size());
        assertEquals(3, result.graph().getRelations().size());
        assertTrue(result.graph().getEntities().stream().anyMatch(entity -> "app-bhf".equals(entity.getId())));
        assertTrue(result.graph().getEntities().stream().anyMatch(entity -> "pod-bhf-1".equals(entity.getId())));
        assertTrue(result.graph().getEntities().stream().anyMatch(entity -> "node-1".equals(entity.getId())));
        assertTrue(result.graph().getRelations().stream()
            .anyMatch(relation -> "contains".equals(relation.getType()) && "app-bhf".equals(relation.getFrom())));
        assertEquals(1, result.matchResult().matchedClusterIds().size());
        assertEquals(1, result.matchResult().matchedServiceIds().size());
    }

    private OperationIntelligenceProperties createProperties(Path tempRoot) {
        OperationIntelligenceProperties properties = new OperationIntelligenceProperties();
        properties.setDataRoot(tempRoot.toString());
        OperationIntelligenceProperties.KnowledgeGraph knowledgeGraph =
            new OperationIntelligenceProperties.KnowledgeGraph();
        knowledgeGraph.setEnabled(true);
        knowledgeGraph.setDataDir("knowledge-graph");
        knowledgeGraph.setResourceSubgraphEnabled(true);
        knowledgeGraph.setResourceSubgraphMaxHops(4);
        knowledgeGraph.setResourceSubgraphRelationTypes(List.of(
            "contains",
            "contains_service",
            "runs_on",
            "belongs_to_cluster"));
        knowledgeGraph.setResourceSubgraphEntityTypes(List.of(
            "MicroService",
            "ApplicationServiceCluster",
            "Pod",
            "WorkerNode"));
        properties.setKnowledgeGraph(knowledgeGraph);
        return properties;
    }

    private GraphSnapshot createEnvironmentSnapshot() {
        GraphEntity microservice = createEntity(
            "svc-sms",
            "MicroService",
            "com.huawei.bes.sm.base.notice.SMSiteMsgUIBOService",
            Map.of("serviceName", "com.huawei.bes.sm.base.notice.SMSiteMsgUIBOService"));
        GraphEntity cluster = createEntity(
            "app-bhf",
            "ApplicationServiceCluster",
            "BHF",
            Map.of(
                "clusterId", "540d4035-e461-4c95-acb3-0f1f187c374d_603_BHF",
                "clusterName", "BHF",
                "neName", "bes_BHF"));
        GraphEntity pod = createEntity("pod-bhf-1", "Pod", "bes_bhf-01", Map.of("podName", "bes_bhf-01"));
        GraphEntity node = createEntity("node-1", "WorkerNode", "192.168.0.10", Map.of("hostIp", "192.168.0.10"));

        GraphSnapshot snapshot = new GraphSnapshot();
        snapshot.setOntologyId("b2b-callchain-v1");
        snapshot.setEnvCode("prod");
        snapshot.setSchemaVersion("1.0");
        snapshot.setSourceSystem("test");
        snapshot.setImportMode("UPSERT");
        snapshot.setSnapshotId("snapshot-1");
        snapshot.setGeneratedAt("2026-05-28T00:00:00Z");
        snapshot.setMetadata(Map.of());
        snapshot.setEntities(List.of(microservice, cluster, pod, node));
        snapshot.setRelations(List.of(
            createRelation("rel-svc-cluster", "belongs_to_cluster", "svc-sms", "app-bhf"),
            createRelation("rel-cluster-pod", "contains", "app-bhf", "pod-bhf-1"),
            createRelation("rel-pod-node", "runs_on", "pod-bhf-1", "node-1")));
        snapshot.setObservations(List.of());
        return snapshot;
    }

    private GraphEntity createEntity(String id, String type, String name, Map<String, Object> properties) {
        GraphEntity entity = new GraphEntity();
        entity.setId(id);
        entity.setType(type);
        entity.setName(name);
        entity.setDisplayName(name);
        entity.setStatus("Normal");
        entity.setProperties(new LinkedHashMap<>(properties));
        return entity;
    }

    private GraphRelation createRelation(String id, String type, String from, String to) {
        GraphRelation relation = new GraphRelation();
        relation.setId(id);
        relation.setType(type);
        relation.setFrom(from);
        relation.setTo(to);
        relation.setProperties(Map.of());
        return relation;
    }
}
