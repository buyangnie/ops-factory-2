/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.callchainsubgraph.service;

import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.GraphEntity;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.GraphSnapshot;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.service.KnowledgeGraphService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the vertical resource-layer subgraph related to call chain entities.
 *
 * @author x00000000
 * @since 2026-05-28
 */
@Service
public class CallChainResourceSubgraphService {
    private static final Set<String> DEFAULT_RELATION_TYPES = Set.of(
        "contains",
        "contains_service",
        "runs_on",
        "belongs_to_cluster",
        "manages");

    private static final Set<String> DEFAULT_RESOURCE_ENTITY_TYPES = Set.of(
        "MicroService",
        "Service",
        "ServiceCluster",
        "Cluster",
        "ApplicationServiceCluster",
        "MiddlewareCluster",
        "Pod",
        "K8sInstance",
        "K8sCluster",
        "WorkerNode",
        "ComputeNode",
        "Host",
        "Container");

    private final OperationIntelligenceProperties properties;

    private final KnowledgeGraphService knowledgeGraphService;

    /**
     * Constructs a CallChainResourceSubgraphService.
     *
     * @param properties the properties
     * @param knowledgeGraphService the knowledge graph service
     */
    public CallChainResourceSubgraphService(OperationIntelligenceProperties properties,
        KnowledgeGraphService knowledgeGraphService) {
        this.properties = properties;
        this.knowledgeGraphService = knowledgeGraphService;
    }

    /**
     * Builds the related resource subgraph for one call chain snapshot.
     *
     * @param ontologyId the ontologyId
     * @param envCode the envCode
     * @param callChainEntities the call chain entities
     * @return the result
     */
    public ResourceSubgraphResult buildResourceSubgraph(String ontologyId, String envCode,
        Collection<GraphEntity> callChainEntities) {
        if (!properties.getKnowledgeGraph().isResourceSubgraphEnabled()) {
            return ResourceSubgraphResult.empty();
        }
        GraphSnapshot envSnapshot;
        try {
            envSnapshot = knowledgeGraphService.getSnapshot(ontologyId, envCode);
        } catch (ResponseStatusException ex) {
            if (HttpStatus.NOT_FOUND.equals(ex.getStatusCode())) {
                return ResourceSubgraphResult.empty();
            }
            throw ex;
        }
        MatchResult matchResult = matchCallChainEntities(envSnapshot, callChainEntities);
        if (matchResult.seedEntityIds().isEmpty()) {
            return new ResourceSubgraphResult(emptySnapshot(ontologyId, envCode), matchResult);
        }
        GraphSnapshot subgraph = knowledgeGraphService.querySubgraph(
            ontologyId,
            envCode,
            matchResult.seedEntityIds(),
            properties.getKnowledgeGraph().getResourceSubgraphMaxHops(),
            resolveRelationTypes());
        GraphSnapshot filtered = filterResourceSubgraph(subgraph, matchResult.seedEntityIds());
        return new ResourceSubgraphResult(filtered, matchResult);
    }

    private MatchResult matchCallChainEntities(GraphSnapshot envSnapshot, Collection<GraphEntity> callChainEntities) {
        Map<String, GraphEntity> envEntitiesById = envSnapshot.getEntities().stream()
            .collect(Collectors.toMap(GraphEntity::getId, entity -> entity, (left, right) -> left,
                LinkedHashMap::new));
        Map<String, List<GraphEntity>> entitiesByClusterId = buildEntityIndex(envSnapshot.getEntities(),
            Set.of("clusterId", "clusterName", "neId"));
        Map<String, List<GraphEntity>> entitiesByServiceName = buildEntityIndex(envSnapshot.getEntities(),
            Set.of("serviceName", "name", "displayName", "clusterName", "neName"));

        Set<String> seedEntityIds = new LinkedHashSet<>();
        Set<String> matchedServiceIds = new LinkedHashSet<>();
        Set<String> matchedClusterIds = new LinkedHashSet<>();
        Set<String> unmatchedServices = new LinkedHashSet<>();

        for (GraphEntity callChainEntity : callChainEntities) {
            if (callChainEntity == null || callChainEntity.getType() == null) {
                continue;
            }
            String entityType = callChainEntity.getType();
            if ("Cluster".equals(entityType)) {
                List<GraphEntity> matchedEntities =
                    entitiesByClusterId.getOrDefault(normalizeLookupKey(callChainEntity.getName()), List.of());
                if (matchedEntities.isEmpty()) {
                    unmatchedServices.add(callChainEntity.getName());
                    continue;
                }
                matchedEntities.forEach(entity -> {
                    seedEntityIds.add(entity.getId());
                    matchedClusterIds.add(entity.getId());
                });
                continue;
            }
            if (!"Service".equals(entityType)) {
                continue;
            }
            Map<String, Object> propertiesMap = callChainEntity.getProperties() == null
                ? Map.of()
                : callChainEntity.getProperties();
            Set<GraphEntity> matchedEntities = new LinkedHashSet<>();
            String clusterId = extractClusterId(propertiesMap);
            if (clusterId != null) {
                matchedEntities.addAll(entitiesByClusterId.getOrDefault(normalizeLookupKey(clusterId), List.of()));
            }
            String serviceName = stringValue(propertiesMap.get("serviceName"));
            if (serviceName != null) {
                matchedEntities.addAll(entitiesByServiceName.getOrDefault(normalizeLookupKey(serviceName), List.of()));
                matchedEntities.addAll(entitiesByServiceName.getOrDefault(normalizeLookupKey(shortName(serviceName)),
                    List.of()));
            }
            if (matchedEntities.isEmpty()) {
                unmatchedServices.add(serviceName == null ? callChainEntity.getName() : serviceName);
                continue;
            }
            boolean matchedCluster = false;
            boolean matchedService = false;
            for (GraphEntity matched : matchedEntities) {
                if (!envEntitiesById.containsKey(matched.getId())) {
                    continue;
                }
                seedEntityIds.add(matched.getId());
                if (isClusterLike(matched)) {
                    matchedClusterIds.add(matched.getId());
                    matchedCluster = true;
                } else {
                    matchedServiceIds.add(matched.getId());
                    matchedService = true;
                }
            }
            if (!matchedCluster && !matchedService) {
                unmatchedServices.add(serviceName == null ? callChainEntity.getName() : serviceName);
            }
        }
        return new MatchResult(seedEntityIds, matchedServiceIds, matchedClusterIds, unmatchedServices);
    }

    private Map<String, List<GraphEntity>> buildEntityIndex(List<GraphEntity> entities, Set<String> propertyKeys) {
        Map<String, List<GraphEntity>> index = new LinkedHashMap<>();
        for (GraphEntity entity : entities) {
            if (entity == null) {
                continue;
            }
            addIndexValue(index, entity.getName(), entity);
            addIndexValue(index, entity.getDisplayName(), entity);
            Map<String, Object> propertiesMap = entity.getProperties() == null ? Map.of() : entity.getProperties();
            for (String propertyKey : propertyKeys) {
                Object value = propertiesMap.get(propertyKey);
                if (value instanceof List<?> values) {
                    values.stream()
                        .map(this::stringValue)
                        .filter(Objects::nonNull)
                        .forEach(item -> addIndexValue(index, item, entity));
                    continue;
                }
                addIndexValue(index, stringValue(value), entity);
            }
        }
        return index;
    }

    private void addIndexValue(Map<String, List<GraphEntity>> index, String rawValue, GraphEntity entity) {
        String normalized = normalizeLookupKey(rawValue);
        if (normalized == null) {
            return;
        }
        index.computeIfAbsent(normalized, key -> new java.util.ArrayList<>()).add(entity);
    }

    private GraphSnapshot filterResourceSubgraph(GraphSnapshot snapshot, Set<String> seedEntityIds) {
        Set<String> allowedTypes = resolveEntityTypes();
        Set<String> selectedEntityIds = snapshot.getEntities().stream()
            .filter(entity -> allowedTypes.contains(entity.getType()) || seedEntityIds.contains(entity.getId()))
            .map(GraphEntity::getId)
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
        GraphSnapshot filtered = new GraphSnapshot();
        filtered.setOntologyId(snapshot.getOntologyId());
        filtered.setEnvCode(snapshot.getEnvCode());
        filtered.setSchemaVersion(snapshot.getSchemaVersion());
        filtered.setSnapshotId(snapshot.getSnapshotId());
        filtered.setGeneratedAt(snapshot.getGeneratedAt());
        filtered.setSourceSystem(snapshot.getSourceSystem());
        filtered.setImportMode("UPSERT");
        filtered.setMetadata(snapshot.getMetadata());
        filtered.setEntities(snapshot.getEntities().stream()
            .filter(entity -> selectedEntityIds.contains(entity.getId()))
            .toList());
        filtered.setRelations(snapshot.getRelations().stream()
            .filter(relation -> selectedEntityIds.contains(relation.getFrom()) && selectedEntityIds.contains(relation.getTo()))
            .toList());
        filtered.setObservations(snapshot.getObservations().stream()
            .filter(observation -> selectedEntityIds.contains(observation.getEntityId()))
            .toList());
        return filtered;
    }

    private GraphSnapshot emptySnapshot(String ontologyId, String envCode) {
        GraphSnapshot snapshot = new GraphSnapshot();
        snapshot.setOntologyId(ontologyId);
        snapshot.setEnvCode(envCode);
        snapshot.setSchemaVersion("1.0");
        snapshot.setSourceSystem("call-chain-resource-subgraph");
        snapshot.setImportMode("UPSERT");
        snapshot.setEntities(List.of());
        snapshot.setRelations(List.of());
        snapshot.setObservations(List.of());
        snapshot.setMetadata(Map.of());
        return snapshot;
    }

    private Set<String> resolveRelationTypes() {
        Set<String> relationTypes = new LinkedHashSet<>(properties.getKnowledgeGraph().getResourceSubgraphRelationTypes());
        if (relationTypes.isEmpty()) {
            relationTypes.addAll(DEFAULT_RELATION_TYPES);
        }
        return relationTypes;
    }

    private Set<String> resolveEntityTypes() {
        Set<String> entityTypes = new LinkedHashSet<>(properties.getKnowledgeGraph().getResourceSubgraphEntityTypes());
        if (entityTypes.isEmpty()) {
            entityTypes.addAll(DEFAULT_RESOURCE_ENTITY_TYPES);
        }
        return entityTypes;
    }

    private boolean isClusterLike(GraphEntity entity) {
        String type = entity.getType();
        return "Cluster".equals(type)
            || "ServiceCluster".equals(type)
            || "ApplicationServiceCluster".equals(type)
            || "MiddlewareCluster".equals(type)
            || "K8sCluster".equals(type);
    }

    private String stringValue(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        return text.trim();
    }

    private String extractClusterId(Map<String, Object> propertiesMap) {
        String clusterId = stringValue(propertiesMap.get("clusterId"));
        if (clusterId != null) {
            return clusterId;
        }
        Object legacyClusterIds = propertiesMap.get("clusterIds");
        if (legacyClusterIds instanceof List<?> values) {
            for (Object value : values) {
                String normalizedValue = stringValue(value);
                if (normalizedValue != null) {
                    return normalizedValue;
                }
            }
        }
        return stringValue(legacyClusterIds);
    }

    private String normalizeLookupKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String shortName(String value) {
        int separatorIndex = Math.max(value.lastIndexOf('.'), value.lastIndexOf('/'));
        return separatorIndex >= 0 && separatorIndex < value.length() - 1 ? value.substring(separatorIndex + 1) : value;
    }

    /**
     * Resource subgraph result.
     */
    public record ResourceSubgraphResult(GraphSnapshot graph, MatchResult matchResult) {
        /**
         * Empty resource result.
         *
         * @return empty result
         */
        public static ResourceSubgraphResult empty() {
            GraphSnapshot snapshot = new GraphSnapshot();
            snapshot.setEntities(List.of());
            snapshot.setRelations(List.of());
            snapshot.setObservations(List.of());
            return new ResourceSubgraphResult(snapshot, MatchResult.empty());
        }
    }

    /**
     * Match result for call chain entities.
     */
    public record MatchResult(Set<String> seedEntityIds, Set<String> matchedServiceIds, Set<String> matchedClusterIds,
                              Set<String> unmatchedServices) {
        /**
         * Empty match result.
         *
         * @return empty result
         */
        public static MatchResult empty() {
            return new MatchResult(Set.of(), Set.of(), Set.of(), Set.of());
        }
    }
}
