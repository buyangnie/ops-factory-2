/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.callchainsubgraph.service;

import com.huawei.opsfactory.operationintelligence.callchainsubgraph.model.CallChainSubgraphRequest;
import com.huawei.opsfactory.operationintelligence.callchainsubgraph.model.CallChainSubgraphResult;
import com.huawei.opsfactory.operationintelligence.callchainsubgraph.model.CallChainSubgraphHistoryItem;
import com.huawei.opsfactory.operationintelligence.callchainsubgraph.store.CallChainSubgraphStore;
import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.GraphEntity;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.GraphObservation;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.GraphRelation;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.GraphSnapshot;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.service.GraphSchemaRegistry;
import com.huawei.opsfactory.operationintelligence.qos.model.CallChainTree;
import com.huawei.opsfactory.operationintelligence.qos.model.CallFlow;
import com.huawei.opsfactory.operationintelligence.qos.model.FlowNode;
import com.huawei.opsfactory.operationintelligence.service.CallChainService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for generating and retrieving short-lived call chain entity subgraphs.
 *
 * @author x00000000
 * @since 2026-05-27
 */
@Service
public class CallChainSubgraphService {
    private static final String ENTRY_CONDITION_KEY = "menuId";

    private static final String BUSINESS_ENTITY_TYPE = "BusinessCapability";

    private static final String MICROSERVICE_ENTITY_TYPE = "Service";

    private static final String CLUSTER_ENTITY_TYPE = "Cluster";

    private static final String CALLS_RELATION_TYPE = "calls";

    private static final String BELONGS_TO_CLUSTER_RELATION_TYPE = "belongs_to_cluster";

    private static final Pattern UNSAFE_CHARS = Pattern.compile("[^A-Za-z0-9_.-]+");

    private final OperationIntelligenceProperties properties;

    private final CallChainService callChainService;

    private final CallChainResourceSubgraphService resourceSubgraphService;

    private final CallChainSubgraphStore store;

    /**
     * Constructs a CallChainSubgraphService.
     *
     * @param properties the operation intelligence properties
     * @param callChainService the call chain service
     * @param store the subgraph store
     */
    public CallChainSubgraphService(OperationIntelligenceProperties properties, CallChainService callChainService,
        CallChainResourceSubgraphService resourceSubgraphService, CallChainSubgraphStore store) {
        this.properties = properties;
        this.callChainService = callChainService;
        this.resourceSubgraphService = resourceSubgraphService;
        this.store = store;
    }

    /**
     * Generates a call chain entity subgraph and stores it temporarily.
     *
     * @param request the request
     * @return the generated result
     */
    public CallChainSubgraphResult generate(CallChainSubgraphRequest request) {
        ensureEnabled();
        requireText(request.getMenuId(), "menuId");
        requireText(request.getEnvCode(), "envCode");
        requireText(request.getSolutionType(), "solutionType");
        String queryMode = resolveQueryMode(request.getMode());
        TimeRange timeRange = resolveTimeRange(request.getStartTime(), request.getEndTime());
        List<Map<String, String>> conditions = List.of(Map.of(
            "conditionKey", ENTRY_CONDITION_KEY,
            "conditionValue", request.getMenuId().trim()));
        CallChainTree callChainTree = callChainService.queryCallChain(
            request.getSolutionType().trim(), conditions, timeRange.startTime(), timeRange.endTime());
        OffsetDateTime generatedAt = OffsetDateTime.now();
        String subgraphId = "cc-subgraph-" + UUID.randomUUID().toString().replace("-", "");
        GraphSnapshot callChainGraph = buildGraphSnapshot(request, callChainTree, subgraphId, generatedAt, queryMode);
        CallChainResourceSubgraphService.ResourceSubgraphResult resourceSubgraphResult = resourceSubgraphService
            .buildResourceSubgraph(resolveOntologyId(request.getOntologyId()), request.getEnvCode().trim(),
                callChainGraph.getEntities());
        GraphSnapshot graph = mergeSnapshots(callChainGraph, resourceSubgraphResult.graph(), subgraphId, generatedAt);
        CallChainSubgraphResult result = new CallChainSubgraphResult();
        result.setSubgraphId(subgraphId);
        result.setMenuId(request.getMenuId().trim());
        result.setEnvCode(request.getEnvCode().trim());
        result.setSolutionType(request.getSolutionType().trim());
        result.setOntologyId(resolveOntologyId(request.getOntologyId()));
        result.setGeneratedAt(generatedAt.toString());
        result.setExpiresAt(generatedAt.plusMinutes(properties.getKnowledgeGraph().getCallChainSubgraphTtlMinutes())
            .toString());
        result.setGraph(graph);
        result.setSummary(buildSummary(callChainTree, graph, resourceSubgraphResult.matchResult()));
        store.save(result);
        return result;
    }

    /**
     * Gets a generated subgraph by id.
     *
     * @param subgraphId the subgraph id
     * @return the result
     */
    public CallChainSubgraphResult get(String subgraphId) {
        ensureEnabled();
        requireText(subgraphId, "subgraphId");
        return store.load(subgraphId.trim())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Call chain subgraph not found"));
    }

    /**
     * Lists generated call chain subgraph history items.
     *
     * @param ontologyId the ontology id
     * @param envCode the env code
     * @return the history items
     */
    public List<CallChainSubgraphHistoryItem> list(String ontologyId, String envCode) {
        ensureEnabled();
        String resolvedOntologyId = resolveOntologyId(ontologyId);
        String normalizedEnvCode = envCode == null || envCode.isBlank() ? null : envCode.trim();
        return store.list().stream()
            .filter(result -> resolvedOntologyId.equals(resolveOntologyId(result.getOntologyId())))
            .filter(result -> normalizedEnvCode == null || normalizedEnvCode.equals(result.getEnvCode()))
            .map(this::toHistoryItem)
            .toList();
    }

    private GraphSnapshot buildGraphSnapshot(CallChainSubgraphRequest request, CallChainTree tree, String subgraphId,
        OffsetDateTime generatedAt, String queryMode) {
        Map<String, GraphEntity> entitiesById = new LinkedHashMap<>();
        Map<String, GraphRelation> relationsById = new LinkedHashMap<>();
        Map<String, ServiceObservationAccumulator> serviceObservations = new LinkedHashMap<>();
        addBusinessEntryEntity(request, entitiesById);
        List<CallFlow> flows = tree.getFlows() == null ? List.of() : tree.getFlows();
        for (CallFlow flow : flows) {
            buildFlowEntities(flow, entitiesById, relationsById, serviceObservations);
        }
        GraphSnapshot snapshot = new GraphSnapshot();
        snapshot.setOntologyId(resolveOntologyId(request.getOntologyId()));
        snapshot.setEnvCode(request.getEnvCode().trim());
        snapshot.setSchemaVersion("1.0");
        snapshot.setSourceSystem("call-chain-subgraph");
        snapshot.setImportMode("UPSERT");
        snapshot.setSnapshotId(subgraphId);
        snapshot.setGeneratedAt(generatedAt.toString());
        snapshot.setMetadata(buildMetadata(request, tree, subgraphId, queryMode));
        snapshot.setEntities(new ArrayList<>(entitiesById.values()));
        snapshot.setRelations(new ArrayList<>(relationsById.values()));
        snapshot.setObservations(buildObservations(generatedAt, serviceObservations, entitiesById));
        return snapshot;
    }

    private void addBusinessEntryEntity(CallChainSubgraphRequest request, Map<String, GraphEntity> entitiesById) {
        String menuId = request.getMenuId().trim();
        String businessEntityId = businessEntityId(menuId);
        putEntity(entitiesById, createEntity(businessEntityId, BUSINESS_ENTITY_TYPE, menuId, menuId, Map.of(
            "menuId", menuId,
            "entryType", ENTRY_CONDITION_KEY,
            "envCode", request.getEnvCode().trim())));
    }

    private void buildFlowEntities(CallFlow flow, Map<String, GraphEntity> entitiesById,
        Map<String, GraphRelation> relationsById, Map<String, ServiceObservationAccumulator> serviceObservations) {
        if (flow == null || flow.getNodes() == null || flow.getNodes().isEmpty()) {
            return;
        }
        String previousEntityId = null;
        for (FlowNode node : flow.getNodes()) {
            if (node == null || node.getServiceName() == null || node.getServiceName().isBlank()) {
                continue;
            }
            String clusterId = resolveClusterId(node);
            String serviceEntityId = serviceEntityId(node.getServiceName(), clusterId);
            putEntity(entitiesById, createMicroserviceEntity(node, serviceEntityId, clusterId, flow.getFlowId()));
            if (previousEntityId != null) {
                putRelation(relationsById, createFlowRelation(previousEntityId, serviceEntityId, flow));
            }
            previousEntityId = serviceEntityId;
            if (clusterId != null) {
                String clusterEntityId = clusterEntityId(clusterId);
                putEntity(entitiesById, createClusterEntity(clusterId, clusterEntityId));
                putRelation(relationsById, createClusterRelation(serviceEntityId, clusterEntityId));
            }
            serviceObservations.computeIfAbsent(serviceEntityId,
                key -> new ServiceObservationAccumulator(node.getServiceName(), serviceEntityId, clusterId))
                .accumulate(flow.getFlowId(), node);
        }
    }

    private Map<String, Object> buildMetadata(CallChainSubgraphRequest request, CallChainTree tree, String subgraphId,
        String queryMode) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("subgraphId", subgraphId);
        metadata.put("subgraphMode", "callChain");
        metadata.put("entryType", ENTRY_CONDITION_KEY);
        metadata.put("menuId", request.getMenuId().trim());
        metadata.put("solutionType", request.getSolutionType().trim());
        metadata.put("mode", queryMode);
        metadata.put("chainType", tree.getChainType());
        metadata.put("flowCount", tree.getFlows() == null ? 0 : tree.getFlows().size());
        metadata.put("totalCount", tree.getTotalCount());
        metadata.put("queryTimeRange", tree.getQueryTimeRange());
        return metadata;
    }

    private Map<String, Object> buildSummary(CallChainTree tree, GraphSnapshot graph,
        CallChainResourceSubgraphService.MatchResult matchResult) {
        Set<String> microserviceIds = graph.getEntities().stream()
            .filter(entity -> MICROSERVICE_ENTITY_TYPE.equals(entity.getType()))
            .map(GraphEntity::getId)
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
        Set<String> clusterIds = graph.getEntities().stream()
            .filter(entity -> CLUSTER_ENTITY_TYPE.equals(entity.getType()))
            .map(GraphEntity::getId)
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("flowCount", tree.getFlows() == null ? 0 : tree.getFlows().size());
        summary.put("entityCount", graph.getEntities().size());
        summary.put("relationCount", graph.getRelations().size());
        summary.put("observationCount", graph.getObservations().size());
        summary.put("microserviceCount", microserviceIds.size());
        summary.put("clusterCount", clusterIds.size());
        summary.put("resourceEntityCount", countResourceEntities(graph));
        summary.put("resourceRelationCount", countResourceRelations(graph));
        summary.put("matchedServiceCount", matchResult.matchedServiceIds().size());
        summary.put("matchedClusterCount", matchResult.matchedClusterIds().size());
        summary.put("unmatchedServiceCount", matchResult.unmatchedServices().size());
        summary.put("unmatchedServices", new ArrayList<>(matchResult.unmatchedServices()));
        return summary;
    }

    private GraphSnapshot mergeSnapshots(GraphSnapshot callChainGraph, GraphSnapshot resourceGraph, String subgraphId,
        OffsetDateTime generatedAt) {
        Map<String, String> clusterEntityMapping = resolveClusterEntityMapping(callChainGraph, resourceGraph);
        Map<String, GraphEntity> entitiesById = new LinkedHashMap<>();
        Map<String, GraphRelation> relationsById = new LinkedHashMap<>();
        Map<String, GraphObservation> observationsById = new LinkedHashMap<>();
        callChainGraph.getEntities().stream()
            .filter(entity -> !clusterEntityMapping.containsKey(entity.getId()))
            .forEach(entity -> entitiesById.put(entity.getId(), entity));
        resourceGraph.getEntities().forEach(entity -> entitiesById.putIfAbsent(entity.getId(), entity));
        callChainGraph.getRelations().stream()
            .map(relation -> remapClusterRelation(relation, clusterEntityMapping))
            .forEach(relation -> relationsById.putIfAbsent(relation.getId(), relation));
        resourceGraph.getRelations().forEach(relation -> relationsById.putIfAbsent(relation.getId(), relation));
        callChainGraph.getObservations().forEach(observation -> observationsById.put(observation.getId(), observation));
        resourceGraph.getObservations().forEach(observation -> observationsById.putIfAbsent(observation.getId(), observation));
        GraphSnapshot merged = new GraphSnapshot();
        merged.setOntologyId(callChainGraph.getOntologyId());
        merged.setEnvCode(callChainGraph.getEnvCode());
        merged.setSchemaVersion(callChainGraph.getSchemaVersion());
        merged.setSourceSystem(callChainGraph.getSourceSystem());
        merged.setImportMode(callChainGraph.getImportMode());
        merged.setSnapshotId(subgraphId);
        merged.setGeneratedAt(generatedAt.toString());
        Map<String, Object> metadata = new LinkedHashMap<>(callChainGraph.getMetadata());
        metadata.put("resourceSubgraphEnabled", properties.getKnowledgeGraph().isResourceSubgraphEnabled());
        metadata.put("resourceEntityCount", countResourceEntities(resourceGraph));
        metadata.put("resourceRelationCount", countResourceRelations(resourceGraph));
        merged.setMetadata(metadata);
        merged.setEntities(new ArrayList<>(entitiesById.values()));
        merged.setRelations(new ArrayList<>(relationsById.values()));
        merged.setObservations(new ArrayList<>(observationsById.values()));
        return merged;
    }

    private Map<String, String> resolveClusterEntityMapping(GraphSnapshot callChainGraph, GraphSnapshot resourceGraph) {
        Map<String, String> resourceClusterIds = new LinkedHashMap<>();
        resourceGraph.getEntities().stream()
            .filter(this::isResourceClusterEntity)
            .forEach(entity -> {
                String clusterId = readClusterId(entity);
                if (clusterId != null) {
                    resourceClusterIds.putIfAbsent(clusterId, entity.getId());
                }
            });
        Map<String, String> mapping = new LinkedHashMap<>();
        callChainGraph.getEntities().stream()
            .filter(entity -> CLUSTER_ENTITY_TYPE.equals(entity.getType()))
            .forEach(entity -> {
                String clusterId = readClusterId(entity);
                if (clusterId == null) {
                    return;
                }
                String resourceEntityId = resourceClusterIds.get(clusterId);
                if (resourceEntityId != null) {
                    mapping.put(entity.getId(), resourceEntityId);
                }
            });
        return mapping;
    }

    private GraphRelation remapClusterRelation(GraphRelation relation, Map<String, String> clusterEntityMapping) {
        if (clusterEntityMapping.isEmpty()) {
            return relation;
        }
        String remappedFrom = clusterEntityMapping.getOrDefault(relation.getFrom(), relation.getFrom());
        String remappedTo = clusterEntityMapping.getOrDefault(relation.getTo(), relation.getTo());
        if (Objects.equals(remappedFrom, relation.getFrom()) && Objects.equals(remappedTo, relation.getTo())) {
            return relation;
        }
        GraphRelation remapped = new GraphRelation();
        remapped.setId("cc-rel-" + sanitizeSegment(remappedFrom + "-" + remappedTo + "-" + relation.getType()));
        remapped.setType(relation.getType());
        remapped.setFrom(remappedFrom);
        remapped.setTo(remappedTo);
        remapped.setProperties(new LinkedHashMap<>(relation.getProperties()));
        return remapped;
    }

    private boolean isResourceClusterEntity(GraphEntity entity) {
        String type = entity.getType();
        return "ApplicationServiceCluster".equals(type)
            || "MiddlewareCluster".equals(type)
            || "K8sCluster".equals(type);
    }

    private String readClusterId(GraphEntity entity) {
        if (entity.getProperties() == null) {
            return null;
        }
        Object clusterId = entity.getProperties().get("clusterId");
        if (clusterId instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        return null;
    }

    private CallChainSubgraphHistoryItem toHistoryItem(CallChainSubgraphResult result) {
        CallChainSubgraphHistoryItem item = new CallChainSubgraphHistoryItem();
        item.setSubgraphId(result.getSubgraphId());
        item.setMenuId(result.getMenuId());
        item.setEnvCode(result.getEnvCode());
        item.setSolutionType(result.getSolutionType());
        item.setOntologyId(resolveOntologyId(result.getOntologyId()));
        item.setGeneratedAt(result.getGeneratedAt());
        item.setExpiresAt(result.getExpiresAt());
        item.setSummary(result.getSummary() == null ? Map.of() : new LinkedHashMap<>(result.getSummary()));
        return item;
    }

    private long countResourceEntities(GraphSnapshot graph) {
        return graph.getEntities().stream()
            .filter(entity -> !BUSINESS_ENTITY_TYPE.equals(entity.getType()))
            .filter(entity -> !MICROSERVICE_ENTITY_TYPE.equals(entity.getType()))
            .filter(entity -> !CLUSTER_ENTITY_TYPE.equals(entity.getType()))
            .count();
    }

    private long countResourceRelations(GraphSnapshot graph) {
        Set<String> callChainEntityIds = graph.getEntities().stream()
            .filter(entity -> BUSINESS_ENTITY_TYPE.equals(entity.getType())
                || MICROSERVICE_ENTITY_TYPE.equals(entity.getType())
                || CLUSTER_ENTITY_TYPE.equals(entity.getType()))
            .map(GraphEntity::getId)
            .collect(Collectors.toSet());
        return graph.getRelations().stream()
            .filter(relation -> !callChainEntityIds.contains(relation.getFrom())
                || !callChainEntityIds.contains(relation.getTo()))
            .count();
    }

    private List<GraphObservation> buildObservations(OffsetDateTime generatedAt,
        Map<String, ServiceObservationAccumulator> serviceObservations, Map<String, GraphEntity> entitiesById) {
        List<GraphObservation> observations = new ArrayList<>();
        for (ServiceObservationAccumulator accumulator : serviceObservations.values()) {
            GraphEntity entity = entitiesById.get(accumulator.entityId);
            if (entity == null) {
                continue;
            }
            long averageCost = accumulator.occurrenceCount > 0
                ? accumulator.totalAvgCost / accumulator.occurrenceCount
                : 0L;
            Map<String, Object> baseProperties = new LinkedHashMap<>();
            baseProperties.put("serviceName", accumulator.serviceName);
            baseProperties.put("clusterId", accumulator.clusterId);
            baseProperties.put("flowCount", accumulator.flowIds.size());
            baseProperties.put("occurrenceCount", accumulator.occurrenceCount);
            observations.add(createObservation(
                accumulator.entityId + "-avg-cost",
                accumulator.entityId,
                generatedAt,
                "avgCost",
                averageCost,
                baseProperties));
            observations.add(createObservation(
                accumulator.entityId + "-min-cost",
                accumulator.entityId,
                generatedAt,
                "minCost",
                accumulator.minCost,
                baseProperties));
            observations.add(createObservation(
                accumulator.entityId + "-max-cost",
                accumulator.entityId,
                generatedAt,
                "maxCost",
                accumulator.maxCost,
                baseProperties));
            Map<String, Object> entityProperties = entity.getProperties() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(entity.getProperties());
            entityProperties.put("avgCost", averageCost);
            entityProperties.put("minCost", accumulator.minCost);
            entityProperties.put("maxCost", accumulator.maxCost);
            entityProperties.put("flowCount", accumulator.flowIds.size());
            entityProperties.put("occurrenceCount", accumulator.occurrenceCount);
            entity.setProperties(entityProperties);
        }
        return observations;
    }

    private GraphObservation createObservation(String observationId, String entityId, OffsetDateTime generatedAt,
        String name, long value, Map<String, Object> baseProperties) {
        GraphObservation observation = new GraphObservation();
        observation.setId("cc-obs-" + sanitizeSegment(observationId));
        observation.setEntityId(entityId);
        observation.setObservedAt(generatedAt.toString());
        observation.setCategory("call-chain");
        observation.setName(name);
        observation.setSeverity("normal");
        observation.setValue(value);
        observation.setUnit("ms");
        observation.setProperties(baseProperties);
        return observation;
    }

    private GraphEntity createMicroserviceEntity(FlowNode node, String serviceEntityId, String clusterId,
        String flowId) {
        String serviceName = node.getServiceName().trim();
        String shortDisplayName = shortName(serviceName);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("serviceName", serviceName);
        properties.put("operationName", node.getOperationName());
        properties.put("seqNo", node.getSeqNo());
        properties.put("flowId", flowId);
        properties.put("clusterId", clusterId);
        return createEntity(serviceEntityId, MICROSERVICE_ENTITY_TYPE, serviceName, shortDisplayName, properties);
    }

    private GraphEntity createClusterEntity(String clusterId, String clusterEntityId) {
        String displayName = shortClusterName(clusterId);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("clusterId", clusterId);
        properties.put("clusterName", displayName);
        return createEntity(clusterEntityId, CLUSTER_ENTITY_TYPE, clusterId, displayName, properties);
    }

    private GraphEntity createEntity(String entityId, String type, String name, String displayName,
        Map<String, Object> properties) {
        GraphEntity entity = new GraphEntity();
        entity.setId(entityId);
        entity.setType(type);
        entity.setName(name);
        entity.setDisplayName(displayName);
        entity.setStatus("Normal");
        entity.setProperties(properties);
        return entity;
    }

    private GraphRelation createFlowRelation(String from, String to, CallFlow flow) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("flowId", flow.getFlowId());
        properties.put("callCount", flow.getCallCount());
        properties.put("callRatio", flow.getCallRatio());
        properties.put("successPercent", flow.getSuccessPercent());
        return createRelation("cc-rel-" + sanitizeSegment(from + "-" + to), CALLS_RELATION_TYPE, from, to, properties);
    }

    private GraphRelation createClusterRelation(String serviceEntityId, String clusterEntityId) {
        return createRelation("cc-rel-" + sanitizeSegment(serviceEntityId + "-" + clusterEntityId),
            BELONGS_TO_CLUSTER_RELATION_TYPE, serviceEntityId, clusterEntityId, Map.of());
    }

    private GraphRelation createRelation(String relationId, String type, String from, String to,
        Map<String, Object> properties) {
        GraphRelation relation = new GraphRelation();
        relation.setId(relationId);
        relation.setType(type);
        relation.setFrom(from);
        relation.setTo(to);
        relation.setProperties(properties);
        return relation;
    }

    private void putEntity(Map<String, GraphEntity> entitiesById, GraphEntity entity) {
        entitiesById.putIfAbsent(entity.getId(), entity);
    }

    private void putRelation(Map<String, GraphRelation> relationsById, GraphRelation relation) {
        GraphRelation existing = relationsById.get(relation.getId());
        if (existing == null) {
            relationsById.put(relation.getId(), relation);
            return;
        }
        Map<String, Object> mergedProperties = existing.getProperties();
        relation.getProperties().forEach(mergedProperties::putIfAbsent);
        existing.setProperties(mergedProperties);
    }

    private TimeRange resolveTimeRange(Long startTime, Long endTime) {
        long configuredMaxRange = properties.getCallChain().getMaxTimeRangeMs();
        long resolvedEndTime = endTime == null ? System.currentTimeMillis() : endTime;
        long resolvedStartTime = startTime == null ? resolvedEndTime - configuredMaxRange : startTime;
        if (resolvedStartTime <= 0 || resolvedEndTime <= 0 || resolvedEndTime <= resolvedStartTime) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid call chain time range");
        }
        if (resolvedEndTime - resolvedStartTime > configuredMaxRange) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Call chain time range exceeds configured limit");
        }
        return new TimeRange(resolvedStartTime, resolvedEndTime);
    }

    private String resolveClusterId(FlowNode node) {
        if (node.getClusterId() != null && !node.getClusterId().isBlank()) {
            return node.getClusterId().trim();
        }
        List<String> clusters = node.getCluster();
        if (clusters == null || clusters.isEmpty()) {
            return null;
        }
        return clusters.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .findFirst()
            .orElse(null);
    }

    private String resolveOntologyId(String ontologyId) {
        return ontologyId == null || ontologyId.isBlank()
            ? GraphSchemaRegistry.DEFAULT_ONTOLOGY_ID
            : ontologyId.trim();
    }

    private String resolveQueryMode(String queryMode) {
        String resolvedMode = queryMode == null || queryMode.isBlank()
            ? properties.getCallChain().getQueryMode()
            : queryMode;
        if (resolvedMode == null || resolvedMode.isBlank()) {
            return "service";
        }
        return resolvedMode.trim();
    }

    private String businessEntityId(String menuId) {
        return "cc-biz-" + sanitizeSegment(menuId);
    }

    private String serviceEntityId(String serviceName, String clusterId) {
        String clusterKey = clusterId == null ? "no-cluster" : clusterId;
        return "cc-svc-" + sanitizeSegment(serviceName) + "-" + sanitizeSegment(clusterKey);
    }

    private String clusterEntityId(String clusterId) {
        return "cc-cluster-" + sanitizeSegment(clusterId);
    }

    private String shortName(String value) {
        int separatorIndex = Math.max(value.lastIndexOf('.'), value.lastIndexOf('/'));
        return separatorIndex >= 0 && separatorIndex < value.length() - 1 ? value.substring(separatorIndex + 1) : value;
    }

    private String shortClusterName(String clusterId) {
        int separatorIndex = clusterId.lastIndexOf('_');
        return separatorIndex >= 0 && separatorIndex < clusterId.length() - 1
            ? clusterId.substring(separatorIndex + 1)
            : clusterId;
    }

    private String sanitizeSegment(String value) {
        String sanitized = UNSAFE_CHARS.matcher(value == null ? "" : value.trim()).replaceAll("-");
        sanitized = sanitized.replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
        return sanitized.isBlank() ? "na" : sanitized.toLowerCase(Locale.ROOT);
    }

    private void ensureEnabled() {
        if (!properties.getKnowledgeGraph().isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge graph is disabled");
        }
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
    }

    private record TimeRange(long startTime, long endTime) {
    }

    private static final class ServiceObservationAccumulator {
        private final String serviceName;

        private final String entityId;

        private final String clusterId;

        private final Set<String> flowIds = new LinkedHashSet<>();

        private long totalAvgCost;

        private long minCost = Long.MAX_VALUE;

        private long maxCost = Long.MIN_VALUE;

        private int occurrenceCount;

        private ServiceObservationAccumulator(String serviceName, String entityId, String clusterId) {
            this.serviceName = serviceName;
            this.entityId = entityId;
            this.clusterId = clusterId;
        }

        private void accumulate(String flowId, FlowNode node) {
            if (flowId != null && !flowId.isBlank()) {
                flowIds.add(flowId);
            }
            totalAvgCost += node.getAvgCost() == null ? 0L : node.getAvgCost();
            minCost = Math.min(minCost, node.getMinCost() == null ? Long.MAX_VALUE : node.getMinCost());
            maxCost = Math.max(maxCost, node.getMaxCost() == null ? Long.MIN_VALUE : node.getMaxCost());
            occurrenceCount++;
        }
    }
}
