/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service;

import com.huawei.opsfactory.gateway.config.GatewayProperties;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @deprecated Use {@link ClusterRelationService} instead. Host-level relations are replaced by cluster-level relations.
 * @author x00000000
 * @since 2026-05-09
 */
@Deprecated
@Service
public class HostRelationService {
    private static final Logger log = LoggerFactory.getLogger(HostRelationService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final GatewayProperties properties;

    private final HostService hostService;

    private final ClusterService clusterService;

    private Path relationsDir;

    private BusinessServiceService businessServiceService;

    /**
     * Creates the host relation service instance.
     */
    public HostRelationService(GatewayProperties properties, HostService hostService, ClusterService clusterService) {
        this.properties = properties;
        this.hostService = hostService;
        this.clusterService = clusterService;
    }

    /**
     * Sets the business service service via lazy injection.
     *
     * @param businessServiceService the business service service via lazy injection
     */
    @Lazy
    @Autowired
    public void setBusinessServiceService(BusinessServiceService businessServiceService) {
        this.businessServiceService = businessServiceService;
    }

    /**
     * Initializes the host relations data directory at startup.
     */
    @PostConstruct
    public void init() {
        Path gatewayRoot = properties.getGatewayRootPath();
        this.relationsDir = gatewayRoot.resolve("data").resolve("host-relations");
        try {
            Files.createDirectories(relationsDir);
        } catch (IOException e) {
            log.error("Failed to create host-relations directory: {}", relationsDir, e);
        }
        log.info("HostRelationService initialized, relationsDir={}", relationsDir);
    }

    // ── CRUD Operations ──────────────────────────────────────────────

    /**
     * List relations with optional filters.
     *
     * @param hostId host identifier
     * @param groupId group identifier
     * @param clusterId cluster identifier
     * @param sourceType source type filter
     * @param sourceId source identifier filter
     * @return the list relations with optional filters
     */
    public List<Map<String, Object>> listRelations(String hostId, String groupId, String clusterId, String sourceType,
        String sourceId) {
        List<Map<String, Object>> relations = new ArrayList<>();
        if (!Files.isDirectory(relationsDir)) {
            return relations;
        }

        List<String> targetHostIds = resolveTargetHostIds(groupId, clusterId);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(relationsDir, "*.json")) {
            for (Path file : stream) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                Map<String, Object> rel = readFile(file);
                if (rel == null || !matchesRelationFilters(rel, hostId, targetHostIds, sourceType, sourceId)) {
                    continue;
                }
                relations.add(rel);
            }
        } catch (IOException e) {
            log.error("Failed to list relations from {}", relationsDir, e);
        }
        return relations;
    }

    private List<String> resolveTargetHostIds(String groupId, String clusterId) {
        if ((groupId == null || groupId.isEmpty()) && (clusterId == null || clusterId.isEmpty())) {
            return null;
        }
        List<String> ids = new ArrayList<>();
        if (clusterId != null && !clusterId.isEmpty()) {
            for (Map<String, Object> h : hostService.listHostsByCluster(clusterId)) {
                ids.add((String) h.get("id"));
            }
        } else {
            for (Map<String, Object> h : hostService.listHostsByGroup(groupId, clusterService)) {
                ids.add((String) h.get("id"));
            }
        }
        return ids;
    }

    private boolean matchesRelationFilters(Map<String, Object> rel, String hostId, List<String> targetHostIds,
        String sourceType, String sourceId) {
        if (hostId != null && !hostId.isEmpty()) {
            String relSourceId = (String) rel.get("sourceHostId");
            String relTargetId = (String) rel.get("targetHostId");
            if (!hostId.equals(relSourceId) && !hostId.equals(relTargetId)) {
                return false;
            }
        }
        if (targetHostIds != null) {
            String relSourceId = (String) rel.get("sourceHostId");
            String relTargetId = (String) rel.get("targetHostId");
            if (!targetHostIds.contains(relSourceId) && !targetHostIds.contains(relTargetId)) {
                return false;
            }
        }
        if (sourceType != null && !sourceType.isEmpty()) {
            String relSourceType = (String) rel.getOrDefault("sourceType", "host");
            if (!sourceType.equals(relSourceType)) {
                return false;
            }
        }
        if (sourceId != null && !sourceId.isEmpty()) {
            String relSourceId = (String) rel.get("sourceHostId");
            if (!sourceId.equals(relSourceId)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Creates a new host relation from the provided field map.
     *
     * @param body request body
     * @return the result
     */
    public Map<String, Object> createRelation(Map<String, Object> body) {
        String sourceHostId = (String) body.get("sourceHostId");
        String targetHostId = (String) body.get("targetHostId");
        String sourceType = (String) body.getOrDefault("sourceType", "host");

        // Validate source based on sourceType
        if ("business-service".equals(sourceType)) {
            try {
                businessServiceService.getBusinessService(sourceHostId);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Source business service not found: " + sourceHostId, e);
            }
        } else {
            try {
                hostService.getHost(sourceHostId);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Source host not found: " + sourceHostId, e);
            }
        }
        // targetHostId always validates as a host
        try {
            hostService.getHost(targetHostId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Target host not found: " + targetHostId, e);
        }

        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        Map<String, Object> relation = new LinkedHashMap<>();
        relation.put("id", id);
        relation.put("sourceType", sourceType);
        relation.put("sourceHostId", sourceHostId);
        relation.put("targetHostId", targetHostId);
        relation.put("description", body.getOrDefault("description", ""));
        relation.put("createdAt", now);
        relation.put("updatedAt", now);

        writeEntityFile(id, relation);
        log.info("Created host relation: id={}, sourceType={}, source={}, target={}", id, sourceType, sourceHostId,
            targetHostId);

        // Sync hostIds on the business service
        if ("business-service".equals(sourceType) && businessServiceService != null) {
            businessServiceService.syncHostIdsFromRelations(sourceHostId);
        }

        return relation;
    }

    /**
     * Updates an existing host relation with the provided field map.
     *
     * @param id an existing host relation with the provided field map
     * @param body an existing host relation with the provided field map
     * @return the result
     */
    public Map<String, Object> updateRelation(String id, Map<String, Object> body) {
        Path file = relationsDir.resolve(id + ".json");
        Map<String, Object> relation = readFile(file);
        if (relation == null) {
            throw new IllegalArgumentException("Host relation not found: " + id);
        }

        String currentSourceType = (String) relation.getOrDefault("sourceType", "host");

        if (body.containsKey("description")) {
            relation.put("description", body.get("description"));
        }
        if (body.containsKey("sourceHostId")) {
            String sourceHostId = (String) body.get("sourceHostId");
            if ("business-service".equals(currentSourceType)) {
                try {
                    businessServiceService.getBusinessService(sourceHostId);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Source business service not found: " + sourceHostId, e);
                }
            } else {
                try {
                    hostService.getHost(sourceHostId);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Source host not found: " + sourceHostId, e);
                }
            }
            relation.put("sourceHostId", sourceHostId);
        }
        if (body.containsKey("targetHostId")) {
            String targetHostId = (String) body.get("targetHostId");
            try {
                hostService.getHost(targetHostId);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Target host not found: " + targetHostId, e);
            }
            relation.put("targetHostId", targetHostId);
        }

        relation.put("updatedAt", Instant.now().toString());
        writeEntityFile(id, relation);
        log.info("Updated host relation: id={}", id);

        // Sync hostIds if this is a business-service relation
        if ("business-service".equals(currentSourceType) && businessServiceService != null) {
            businessServiceService.syncHostIdsFromRelations((String) relation.get("sourceHostId"));
        }

        return relation;
    }

    /**
     * Deletes a host relation by its ID.
     *
     * @param id entity identifier
     * @return the result
     */
    public boolean deleteRelation(String id) {
        Path file = relationsDir.resolve(id + ".json");
        try {
            if (Files.exists(file)) {
                // Read relation before delete to support sync
                Map<String, Object> rel = readFile(file);
                String sourceType = rel != null ? (String) rel.getOrDefault("sourceType", "host") : "host";
                String sourceHostId = rel != null ? (String) rel.get("sourceHostId") : null;

                Files.delete(file);
                log.info("Deleted host relation: id={}", id);

                // Sync hostIds if this was a business-service relation
                if ("business-service".equals(sourceType) && sourceHostId != null && businessServiceService != null) {
                    businessServiceService.syncHostIdsFromRelations(sourceHostId);
                }
                return true;
            }
            return false;
        } catch (IOException e) {
            log.error("Failed to delete relation file: {}", file, e);
            return false;
        }
    }

    /**
     * Delete all relations involving a specific host (for cascade delete).
     *
     * @param hostId host identifier
     */
    public void deleteRelationsByHost(String hostId) {
        List<Map<String, Object>> relations = listRelations(hostId, null, null, null, null);
        for (Map<String, Object> rel : relations) {
            String relId = (String) rel.get("id");
            deleteRelation(relId);
        }
        if (!relations.isEmpty()) {
            log.info("Cascade deleted {} relations for host {}", relations.size(), hostId);
        }
    }

    /**
     * Delete all relations where source is a specific business service (for cascade delete).
     *
     * @param bsId bs id
     */
    public void deleteRelationsByBusinessService(String bsId) {
        List<Map<String, Object>> all = listRelations(null, null, null, "business-service", bsId);
        for (Map<String, Object> rel : all) {
            deleteRelation((String) rel.get("id"));
        }
        if (!all.isEmpty()) {
            log.info("Cascade deleted {} relations for business service {}", all.size(), bsId);
        }
    }

    /**
     * Build ECharts graph data (nodes + edges) for a given group.
     * Includes all hosts in the group plus any related hosts from other groups.
     *
     * @param groupId group identifier
     * @param clusterId cluster identifier
     * @return graph data containing nodes and edges
     */
    public Map<String, Object> getGraphData(String groupId, String clusterId) {
        Map<String, Map<String, Object>> hostMap = collectGroupHosts(groupId, clusterId);
        List<Map<String, Object>> matchedEdges = new ArrayList<>();

        processRelationsForGraph(hostMap, matchedEdges);

        // Build cluster lookup for type info
        Map<String, Map<String, Object>> clusterMap = new LinkedHashMap<>();
        for (Map<String, Object> c : clusterService.listClusters(null, null)) {
            clusterMap.put((String) c.get("id"), c);
        }

        List<Map<String, Object>> nodes = buildGraphNodes(hostMap, clusterMap);
        List<Map<String, Object>> edges = buildGraphEdges(matchedEdges);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodes);
        result.put("edges", edges);
        return result;
    }

    private Map<String, Map<String, Object>> collectGroupHosts(String groupId, String clusterId) {
        List<Map<String, Object>> groupHosts;
        if (clusterId != null && !clusterId.isEmpty()) {
            groupHosts = hostService.listHostsByCluster(clusterId);
        } else if (groupId != null && !groupId.isEmpty()) {
            groupHosts = hostService.listHostsByGroup(groupId, clusterService);
        } else {
            groupHosts = hostService.listHosts(new String[0]);
        }

        Map<String, Map<String, Object>> hostMap = new LinkedHashMap<>();
        for (Map<String, Object> h : groupHosts) {
            hostMap.put((String) h.get("id"), h);
        }
        return hostMap;
    }

    private void processRelationsForGraph(Map<String, Map<String, Object>> hostMap,
        List<Map<String, Object>> matchedEdges) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(relationsDir, "*.json")) {
            for (Path file : stream) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                Map<String, Object> rel = readFile(file);
                if (rel == null) {
                    continue;
                }
                // Skip business-service relations; they are synthesized by enrichWithBusinessServices
                String relSourceType = (String) rel.getOrDefault("sourceType", "host");
                if ("business-service".equals(relSourceType)) {
                    continue;
                }
                String sourceId = (String) rel.get("sourceHostId");
                String targetId = (String) rel.get("targetHostId");
                boolean added = false;
                // Outgoing: source is in the selected group/cluster -> fetch target as +1-hop
                if (hostMap.containsKey(sourceId)) {
                    matchedEdges.add(rel);
                    added = true;
                    addHopHost(!hostMap.containsKey(targetId), targetId, hostMap);
                }
                // Incoming: target is in the selected group/cluster -> fetch source as +1-hop
                if (hostMap.containsKey(targetId) && !added) {
                    matchedEdges.add(rel);
                    addHopHost(!hostMap.containsKey(sourceId), sourceId, hostMap);
                }
            }
        } catch (IOException e) {
            log.error("Failed to read relations", e);
        }
    }

    private void addHopHost(boolean needed, String hostId, Map<String, Map<String, Object>> hostMap) {
        if (!needed) {
            return;
        }
        try {
            hostMap.put(hostId, hostService.getHost(hostId));
        } catch (IllegalArgumentException e) {
            log.debug("Skipping missing host {} while building topology", hostId);
        }
    }

    private List<Map<String, Object>> buildGraphNodes(Map<String, Map<String, Object>> hostMap,
        Map<String, Map<String, Object>> clusterMap) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Map<String, Object> h : hostMap.values()) {
            nodes.add(buildHostNode(h, clusterMap));
        }
        return nodes;
    }

    private List<Map<String, Object>> buildGraphEdges(List<Map<String, Object>> matchedEdges) {
        List<Map<String, Object>> edges = new ArrayList<>();
        for (Map<String, Object> rel : matchedEdges) {
            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("source", rel.get("sourceHostId"));
            edge.put("target", rel.get("targetHostId"));
            edge.put("description", rel.get("description"));
            edges.add(edge);
        }
        return edges;
    }

    /**
     * Get 1-hop neighbors (upstream + downstream) for a given host.
     *
     * @param hostId host identifier
     * @return 1-hop neighbors (upstream + downstream) for a given host
     */
    public Map<String, Object> getNeighbors(String hostId) {
        // 1. Validate host exists
        Map<String, Object> host = hostService.getHost(hostId);

        // 2. Query all relations involving this host
        List<Map<String, Object>> relations = listRelations(hostId, null, null, null, null);

        // 3. Build cluster lookup table
        Map<String, Map<String, Object>> clusterMap = new LinkedHashMap<>();
        for (Map<String, Object> c : clusterService.listClusters(null, null)) {
            clusterMap.put((String) c.get("id"), c);
        }

        // 4. Build current host node
        Map<String, Object> hostNode = buildHostNode(host, clusterMap);

        // 5. Iterate relations, collect upstream and downstream neighbors
        List<Map<String, Object>> upstream = new ArrayList<>();
        List<Map<String, Object>> downstream = new ArrayList<>();

        for (Map<String, Object> rel : relations) {
            String sourceId = (String) rel.get("sourceHostId");
            String targetId = (String) rel.get("targetHostId");
            String direction;
            String neighborId;

            if (hostId.equals(sourceId)) {
                // Current host is source → neighbor is downstream
                direction = "outgoing";
                neighborId = targetId;
            } else {
                // Current host is target → neighbor is upstream
                direction = "incoming";
                neighborId = sourceId;
            }

            try {
                Map<String, Object> neighborHost = hostService.getHost(neighborId);
                Map<String, Object> neighborNode = buildHostNode(neighborHost, clusterMap);

                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("host", neighborNode);
                entry.put("direction", direction);
                entry.put("relationId", rel.get("id"));
                entry.put("relationDescription", rel.get("description"));

                if ("incoming".equals(direction)) {
                    upstream.add(entry);
                } else {
                    downstream.add(entry);
                }
            } catch (IllegalArgumentException e) {
                log.debug("Skipping missing neighbor host {} while building host relation topology", neighborId);
            }
        }

        // 6. Assemble result
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("host", hostNode);
        result.put("upstream", upstream);
        result.put("downstream", downstream);
        result.put("totalNeighbors", upstream.size() + downstream.size());
        return result;
    }

    private Map<String, Object> buildHostNode(Map<String, Object> h, Map<String, Map<String, Object>> clusterMap) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", h.get("id"));
        node.put("name", h.get("name"));
        node.put("ip", h.get("ip"));
        node.put("businessIp", h.get("businessIp"));
        String hostClusterId = h.get("clusterId") != null ? h.get("clusterId").toString() : null;
        Map<String, Object> cluster = hostClusterId != null ? clusterMap.get(hostClusterId) : null;
        node.put("clusterType", cluster != null ? cluster.get("type") : null);
        node.put("clusterName", cluster != null ? cluster.get("name") : null);
        node.put("purpose", h.get("purpose"));
        node.put("groupId", cluster != null ? cluster.get("groupId") : null);
        node.put("tags", h.get("tags"));
        return node;
    }

    // ── File I/O Helpers ─────────────────────────────────────────────

    private Map<String, Object> readFile(Path file) {
        if (!Files.exists(file)) {
            return null;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (IOException e) {
            log.error("Failed to read relation file: {}", file, e);
            return null;
        }
    }

    private void writeEntityFile(String id, Map<String, Object> entity) {
        try {
            Files.createDirectories(relationsDir);
            Path file = relationsDir.resolve(id + ".json");
            String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(entity);
            Files.writeString(file, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to write relation file for id={}", id, e);
            throw new IllegalStateException("Failed to save host relation", e);
        }
    }
}
