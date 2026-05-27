/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.knowledgegraph.service;

import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.GraphEntity;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.GraphExportPackage;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.GraphObservation;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.GraphOntology;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.GraphSnapshot;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.store.GraphOntologyStore;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.store.GraphSnapshotStore;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.store.InMemoryGraphStore;

import jakarta.annotation.PostConstruct;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Knowledge graph service.
 *
 * @author x00000000
 * @since 2026-05-20
 */
@Service
public class KnowledgeGraphService {
    private static final String DEFAULT_SCHEMA_VERSION = "1.0";

    private static final String EXPORT_FORMAT = "KG_NATIVE_JSON";

    private static final Pattern SAFE_ID_PATTERN = Pattern.compile("[A-Za-z0-9_.-]+");

    private final Map<String, ReentrantLock> ontologyLocks = new ConcurrentHashMap<>();

    private final OperationIntelligenceProperties properties;

    private final GraphSchemaRegistry schemaRegistry;

    private final InMemoryGraphStore graphStore;

    private final GraphOntologyStore ontologyStore;

    private final GraphSnapshotStore snapshotStore;

    /**
     * Constructs a KnowledgeGraphService.
     *
     * @param properties the operation intelligence properties
     * @param schemaRegistry the schema registry
     * @param graphStore the in-memory graph store
     * @param ontologyStore the ontology file store
     * @param snapshotStore the snapshot file store
     */
    public KnowledgeGraphService(OperationIntelligenceProperties properties, GraphSchemaRegistry schemaRegistry,
        InMemoryGraphStore graphStore, GraphOntologyStore ontologyStore, GraphSnapshotStore snapshotStore) {
        this.properties = properties;
        this.schemaRegistry = schemaRegistry;
        this.graphStore = graphStore;
        this.ontologyStore = ontologyStore;
        this.snapshotStore = snapshotStore;
    }

    /**
     * Loads persisted snapshots on startup.
     */
    @PostConstruct
    public void init() {
        if (!properties.getKnowledgeGraph().isEnabled()) {
            return;
        }
        for (GraphOntology ontology : ontologyStore.loadAll()) {
            schemaRegistry.register(ontology);
        }
        for (GraphSnapshot snapshot : snapshotStore.loadLatestAll()) {
            graphStore.loadSnapshot(snapshot);
        }
    }

    /**
     * Imports or updates an ontology.
     *
     * @param ontology the ontology
     * @return imported ontology
     */
    public GraphOntology importOntology(GraphOntology ontology) {
        ensureEnabled();
        requireSafeId(ontology.getOntologyId(), "ontologyId");
        GraphOntology registered = schemaRegistry.register(ontology);
        ontologyStore.save(registered);
        return registered;
    }

    /**
     * Lists ontologies.
     *
     * @return ontologies
     */
    public List<GraphOntology> listOntologies() {
        ensureEnabled();
        return schemaRegistry.listOntologies();
    }

    /**
     * Gets ontology.
     *
     * @param ontologyId the ontologyId
     * @return ontology
     */
    public GraphOntology getOntology(String ontologyId) {
        ensureEnabled();
        return schemaRegistry.getOntology(ontologyId);
    }

    /**
     * Deletes one ontology when it has no entity snapshots.
     *
     * @param ontologyId the ontologyId
     * @return the result
     */
    public Map<String, Object> deleteOntology(String ontologyId) {
        ensureEnabled();
        requireSafeId(ontologyId, "ontologyId");
        ReentrantLock lock = ontologyLocks.computeIfAbsent(ontologyId, key -> new ReentrantLock());
        lock.lock();
        try {
            schemaRegistry.getOntology(ontologyId);
            int loadedSnapshotCount = graphStore.countOntologySnapshots(ontologyId);
            int persistedSnapshotCount = snapshotStore.countOntologySnapshots(ontologyId);
            int snapshotCount = Math.max(loadedSnapshotCount, persistedSnapshotCount);
            if (snapshotCount > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ontology has entity snapshots. Delete entities before deleting ontology.");
            }
            GraphOntology deleted = schemaRegistry.deleteOntology(ontologyId);
            ontologyStore.delete(ontologyId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ontologyId", deleted.getOntologyId());
            result.put("deleted", true);
            return result;
        } finally {
            lock.unlock();
            ontologyLocks.remove(ontologyId);
        }
    }

    /**
     * Lists entity environments under one ontology.
     *
     * @param ontologyId the ontologyId
     * @return environments
     */
    public List<Map<String, Object>> listEnvironments(String ontologyId) {
        ensureEnabled();
        ontologyId = resolveOntologyId(ontologyId);
        schemaRegistry.getOntology(ontologyId);
        Set<String> envCodes = new LinkedHashSet<>();
        envCodes.addAll(graphStore.listEnvironmentCodes(ontologyId));
        envCodes.addAll(snapshotStore.listEnvironmentCodes(ontologyId));
        String resolvedOntologyId = ontologyId;
        return envCodes.stream().sorted().map(envCode -> toEnvironment(resolvedOntologyId, envCode)).toList();
    }

    /**
     * Imports graph data.
     *
     * @param request the request
     * @return the result
     */
    public Map<String, Object> importGraph(GraphSnapshot request) {
        ensureEnabled();
        prepareDefaults(request);
        requireSafeId(request.getOntologyId(), "ontologyId");
        requireSafeId(request.getEnvCode(), "envCode");
        requireText(request.getSourceSystem(), "sourceSystem");
        if (!"UPSERT".equals(request.getImportMode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only UPSERT importMode is supported");
        }
        GraphSnapshot merged = graphStore.mergeSnapshot(request);
        schemaRegistry.validate(merged);
        snapshotStore.save(merged);
        graphStore.loadSnapshot(merged);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("envCode", merged.getEnvCode());
        result.put("snapshotId", merged.getSnapshotId());
        result.put("entityCount", merged.getEntities().size());
        result.put("relationCount", merged.getRelations().size());
        result.put("observationCount", merged.getObservations().size());
        return result;
    }

    /**
     * Deletes one environment entity snapshot under an ontology.
     *
     * @param ontologyId the ontologyId
     * @param envCode the envCode
     * @return the result
     */
    public Map<String, Object> deleteEntities(String ontologyId, String envCode) {
        ensureEnabled();
        ontologyId = resolveOntologyId(ontologyId);
        requireSafeId(envCode, "envCode");
        schemaRegistry.getOntology(ontologyId);
        boolean existed = graphStore.deleteSnapshot(ontologyId, envCode);
        snapshotStore.deleteSnapshot(ontologyId, envCode);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ontologyId", ontologyId);
        result.put("envCode", envCode);
        result.put("deleted", existed);
        return result;
    }

    /**
     * Gets entity details.
     *
     * @param envCode the envCode
     * @param entityId the entityId
     * @return the result
     */
    public GraphEntity getEntity(String ontologyId, String envCode, String entityId) {
        ensureEnabled();
        ontologyId = resolveOntologyId(ontologyId);
        requireSafeId(envCode, "envCode");
        requireText(entityId, "entityId");
        return graphStore.getEntity(ontologyId, envCode, entityId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found"));
    }

    /**
     * Queries subgraph.
     *
     * @param envCode the envCode
     * @param entityId the entityId
     * @param maxHops the maxHops
     * @return the result
     */
    public GraphSnapshot querySubgraph(String ontologyId, String envCode, String entityId, int maxHops) {
        ensureEnabled();
        ontologyId = resolveOntologyId(ontologyId);
        requireSafeId(envCode, "envCode");
        requireText(entityId, "entityId");
        int configuredMaxHops = Math.max(properties.getKnowledgeGraph().getMaxHops(), 1);
        if (maxHops < 0 || maxHops > configuredMaxHops) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "maxHops must be between 0 and " + configuredMaxHops);
        }
        return graphStore.querySubgraph(ontologyId, envCode, entityId, maxHops)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found"));
    }

    /**
     * Queries a directed subgraph.
     *
     * @param ontologyId the ontologyId
     * @param envCode the envCode
     * @param entityId the entityId
     * @param upstreamHops the upstreamHops
     * @param downstreamHops the downstreamHops
     * @return the result
     */
    public GraphSnapshot querySubgraph(String ontologyId, String envCode, String entityId, int upstreamHops,
        int downstreamHops) {
        ensureEnabled();
        ontologyId = resolveOntologyId(ontologyId);
        requireSafeId(envCode, "envCode");
        requireText(entityId, "entityId");
        validateHops("upstreamHops", upstreamHops);
        validateHops("downstreamHops", downstreamHops);
        return graphStore.querySubgraph(ontologyId, envCode, entityId, upstreamHops, downstreamHops)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found"));
    }

    /**
     * Exports a native knowledge graph package.
     *
     * @param envCode the envCode
     * @return the result
     */
    public GraphExportPackage exportGraph(String ontologyId, String envCode) {
        ensureEnabled();
        ontologyId = resolveOntologyId(ontologyId);
        requireSafeId(envCode, "envCode");
        GraphSnapshot snapshot = graphStore.getSnapshot(ontologyId, envCode)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Graph snapshot not found"));
        GraphExportPackage exportPackage = new GraphExportPackage();
        exportPackage.setManifest(createExportManifest(snapshot));
        exportPackage.setOntology(schemaRegistry.getOntology(ontologyId));
        exportPackage.setSchemaDsl(schemaRegistry.exportSchemaDsl(ontologyId));
        exportPackage.setSnapshot(snapshot);
        return exportPackage;
    }

    /**
     * Gets entities grouped as a lightweight resource tree.
     *
     * @param envCode the envCode
     * @return the result
     */
    public Map<String, Object> getResourceTree(String ontologyId, String envCode) {
        GraphSnapshot snapshot = getRequiredSnapshot(ontologyId, envCode);
        Map<String,
            List<GraphEntity>> grouped = snapshot.getEntities()
                .stream()
                .collect(Collectors.groupingBy(GraphEntity::getType, LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> roots =
            grouped.entrySet().stream().map(entry -> toResourceGroup(entry.getKey(), entry.getValue())).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("envCode", snapshot.getEnvCode());
        result.put("total", snapshot.getEntities().size());
        result.put("roots", roots);
        return result;
    }

    /**
     * Queries graph observations.
     *
     * @param request the request
     * @return the result
     */
    public Map<String, Object> queryObservations(Map<String, Object> request) {
        String ontologyId = resolveOntologyId(stringValue(request.get("ontologyId")));
        String envCode = stringValue(request.get("envCode"));
        GraphSnapshot snapshot = getRequiredSnapshot(ontologyId, envCode);
        String entityId = stringValue(request.get("entityId"));
        String category = stringValue(request.get("category"));
        String severity = stringValue(request.get("severity"));
        String name = stringValue(request.get("name"));
        int limit = boundedLimit(request.get("limit"), 100, 500);
        List<GraphObservation> observations = snapshot.getObservations()
            .stream()
            .filter(observation -> matches(entityId, observation.getEntityId()))
            .filter(observation -> matches(category, observation.getCategory()))
            .filter(observation -> matches(severity, observation.getSeverity()))
            .filter(observation -> matches(name, observation.getName()))
            .sorted(Comparator.comparing(GraphObservation::getObservedAt).reversed())
            .limit(limit)
            .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("envCode", snapshot.getEnvCode());
        result.put("total", observations.size());
        result.put("results", observations);
        return result;
    }

    /**
     * Finds an impact path between two entities.
     *
     * @param request the request
     * @return the result
     */
    public Map<String, Object> findImpactPath(Map<String, Object> request) {
        String ontologyId = resolveOntologyId(stringValue(request.get("ontologyId")));
        String envCode = stringValue(request.get("envCode"));
        String fromEntityId = stringValue(request.get("fromEntityId"));
        String toEntityId = stringValue(request.get("toEntityId"));
        int maxHops = boundedHops(request.get("maxHops"), 4);
        requireSafeId(envCode, "envCode");
        requireText(fromEntityId, "fromEntityId");
        requireText(toEntityId, "toEntityId");
        GraphSnapshot path = graphStore.findPath(ontologyId, envCode, fromEntityId, toEntityId, maxHops)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Impact path not found"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("envCode", path.getEnvCode());
        result.put("found", true);
        result.put("hopCount", path.getRelations().size());
        result.put("path", path);
        return result;
    }

    /**
     * Gets root cause candidates from abnormal observations.
     *
     * @param request the request
     * @return the result
     */
    public Map<String, Object> getRootCauseCandidates(Map<String, Object> request) {
        String ontologyId = resolveOntologyId(stringValue(request.get("ontologyId")));
        String envCode = stringValue(request.get("envCode"));
        GraphSnapshot snapshot = getRequiredSnapshot(ontologyId, envCode);
        String entityId = stringValue(request.get("entityId"));
        int limit = boundedLimit(request.get("limit"), 10, 100);
        Set<String> entityScope = entityId == null || entityId.isBlank()
            ? snapshot.getEntities().stream().map(GraphEntity::getId).collect(Collectors.toSet())
            : querySubgraph(ontologyId, envCode, entityId, boundedHops(request.get("maxHops"), 4)).getEntities()
                .stream()
                .map(GraphEntity::getId)
                .collect(Collectors.toSet());
        List<Map<String, Object>> candidates = snapshot.getObservations()
            .stream()
            .filter(observation -> entityScope.contains(observation.getEntityId()))
            .filter(observation -> !"normal".equals(observation.getSeverity() == null
                ? null : observation.getSeverity().toLowerCase(Locale.ROOT)))
            .map(observation -> toRootCauseCandidate(snapshot, observation))
            .filter(Objects::nonNull)
            .sorted(candidateComparator())
            .limit(limit)
            .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("envCode", snapshot.getEnvCode());
        result.put("results", candidates);
        result.put("total", candidates.size());
        return result;
    }

    /**
     * Gets an Agent-friendly diagnosis context.
     *
     * @param request the request
     * @return the result
     */
    public Map<String, Object> getDiagnosisContext(Map<String, Object> request) {
        String envCode = stringValue(request.get("envCode"));
        String ontologyId = resolveOntologyId(stringValue(request.get("ontologyId")));
        String entityId = stringValue(request.get("entityId"));
        int maxHops = boundedHops(request.get("maxHops"), 2);
        GraphEntity entity = getEntity(ontologyId, envCode, entityId);
        GraphSnapshot subgraph = querySubgraph(ontologyId, envCode, entityId, maxHops);
        Map<String, Object> candidatesRequest = new LinkedHashMap<>(request);
        candidatesRequest.put("limit", boundedLimit(request.get("candidateLimit"), 5, 20));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("envCode", envCode);
        result.put("entity", entity);
        result.put("subgraph", subgraph);
        result.put("observations", subgraph.getObservations());
        result.put("rootCauseCandidates", getRootCauseCandidates(candidatesRequest).get("results"));
        return result;
    }

    private Map<String, Object> createExportManifest(GraphSnapshot snapshot) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("packageVersion", "1.0");
        manifest.put("format", EXPORT_FORMAT);
        manifest.put("ontologyId", snapshot.getOntologyId());
        manifest.put("envCode", snapshot.getEnvCode());
        manifest.put("schemaVersion", snapshot.getSchemaVersion());
        manifest.put("snapshotId", snapshot.getSnapshotId());
        manifest.put("exportedAt", OffsetDateTime.now().toString());
        manifest.put("entityCount", snapshot.getEntities().size());
        manifest.put("relationCount", snapshot.getRelations().size());
        manifest.put("observationCount", snapshot.getObservations().size());
        return manifest;
    }

    private Map<String, Object> toEnvironment(String ontologyId, String envCode) {
        Map<String, Object> environment = new LinkedHashMap<>();
        environment.put("envCode", envCode);
        environment.put("envName", resolveEnvironmentName(ontologyId, envCode));
        return environment;
    }

    private String resolveEnvironmentName(String ontologyId, String envCode) {
        return graphStore.getSnapshot(ontologyId, envCode)
            .or(() -> snapshotStore.loadLatest(ontologyId, envCode))
            .map(snapshot -> {
                Map<String, Object> metadata = snapshot.getMetadata();
                String envName = metadata != null ? stringValue(metadata.get("envName")) : null;
                String envNameAlt = metadata != null ? stringValue(metadata.get("environmentName")) : null;
                String entityEnvName = snapshot.getEntities()
                    .stream()
                    .map(entity -> {
                        Map<String, Object> props = entity.getProperties();
                        return props != null ? stringValue(props.get("environmentName")) : null;
                    })
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .orElse(null);
                return firstNonBlank(
                    Arrays.asList(envName, envNameAlt, entityEnvName, envCode));
            })
            .orElse(envCode);
    }

    private GraphSnapshot getRequiredSnapshot(String ontologyId, String envCode) {
        ensureEnabled();
        ontologyId = resolveOntologyId(ontologyId);
        requireSafeId(envCode, "envCode");
        return graphStore.getSnapshot(ontologyId, envCode)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Graph snapshot not found"));
    }

    private Map<String, Object> toResourceGroup(String type, List<GraphEntity> entities) {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("id", type);
        group.put("type", type);
        group.put("name", type);
        group.put("count", entities.size());
        group.put("children",
            entities.stream().sorted(Comparator.comparing(GraphEntity::getId)).map(this::toResourceNode).toList());
        return group;
    }

    private Map<String, Object> toResourceNode(GraphEntity entity) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", entity.getId());
        node.put("type", entity.getType());
        node.put("name", entity.getName());
        node.put("displayName", entity.getDisplayName());
        node.put("status", entity.getStatus());
        return node;
    }

    private Map<String, Object> toRootCauseCandidate(GraphSnapshot snapshot, GraphObservation observation) {
        GraphEntity entity = snapshot.getEntities()
            .stream()
            .filter(item -> observation.getEntityId().equals(item.getId()))
            .findFirst()
            .orElse(null);
        if (entity == null) {
            return null;
        }
        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("entityId", entity.getId());
        candidate.put("entityType", entity.getType());
        candidate.put("displayName", entity.getDisplayName());
        candidate.put("severity", observation.getSeverity());
        candidate.put("score", severityScore(observation.getSeverity()));
        candidate.put("evidence", observation);
        return candidate;
    }

    private Comparator<Map<String, Object>> candidateComparator() {
        return Comparator.<Map<String, Object>, Integer> comparing(candidate -> (Integer) candidate.get("score"))
            .reversed();
    }

    private boolean matches(String expected, String actual) {
        return expected == null || expected.isBlank()
            || expected.toLowerCase(Locale.ROOT).equals(actual == null ? null : actual.toLowerCase(Locale.ROOT));
    }

    private int severityScore(String severity) {
        if (severity == null) {
            return 20;
        }
        switch (severity.toLowerCase(Locale.ROOT)) {
            case "critical":
                return 100;
            case "major":
                return 80;
            case "warning":
                return 60;
            case "minor":
                return 40;
            default:
                return 20;
        }
    }

    private int boundedHops(Object value, int defaultValue) {
        int maxHops = value == null ? defaultValue : intValue(value);
        validateHops("maxHops", maxHops);
        return maxHops;
    }

    private void validateHops(String fieldName, int hops) {
        int configuredMaxHops = Math.max(properties.getKnowledgeGraph().getMaxHops(), 1);
        if (hops < 0 || hops > configuredMaxHops) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                fieldName + " must be between 0 and " + configuredMaxHops);
        }
    }

    private int boundedLimit(Object value, int defaultValue, int maxValue) {
        int limit = value == null ? defaultValue : intValue(value);
        if (limit < 1 || limit > maxValue) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and " + maxValue);
        }
        return limit;
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid integer value: " + value);
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid integer value: " + value);
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private String firstNonBlank(List<String> values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void prepareDefaults(GraphSnapshot request) {
        if (request.getOntologyId() == null || request.getOntologyId().isBlank()) {
            request.setOntologyId(GraphSchemaRegistry.DEFAULT_ONTOLOGY_ID);
        }
        if (request.getSchemaVersion() == null || request.getSchemaVersion().isBlank()) {
            request.setSchemaVersion(DEFAULT_SCHEMA_VERSION);
        }
        if (request.getImportMode() == null || request.getImportMode().isBlank()) {
            request.setImportMode("UPSERT");
        }
        if (request.getSnapshotId() == null || request.getSnapshotId().isBlank()) {
            request.setSnapshotId("kg-" + request.getEnvCode() + "-" + System.currentTimeMillis());
        }
        if (request.getGeneratedAt() == null || request.getGeneratedAt().isBlank()) {
            request.setGeneratedAt(OffsetDateTime.now().toString());
        }
    }

    private String resolveOntologyId(String ontologyId) {
        String resolvedOntologyId =
            ontologyId == null || ontologyId.isBlank() ? GraphSchemaRegistry.DEFAULT_ONTOLOGY_ID : ontologyId;
        requireSafeId(resolvedOntologyId, "ontologyId");
        return resolvedOntologyId;
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

    private void requireSafeId(String value, String fieldName) {
        requireText(value, fieldName);
        if (!SAFE_ID_PATTERN.matcher(value).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                fieldName + " contains unsupported path characters");
        }
    }
}
