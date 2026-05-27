/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.knowledgegraph.store;

import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.GraphEntity;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.GraphObservation;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.GraphRelation;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.GraphSnapshot;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * In-memory graph store.
 *
 * @author x00000000
 * @since 2026-05-20
 */
@Component
public class InMemoryGraphStore {
    private final Map<String, EnvGraph> graphs = new ConcurrentHashMap<>();

    /**
     * Loads a complete environment snapshot.
     *
     * @param snapshot the snapshot
     */
    public void loadSnapshot(GraphSnapshot snapshot) {
        EnvGraph envGraph = EnvGraph.fromSnapshot(snapshot);
        graphs.put(graphKey(snapshot.getOntologyId(), snapshot.getEnvCode()), envGraph);
    }

    /**
     * Upserts import data into one environment.
     *
     * @param incoming the incoming snapshot
     * @return merged snapshot
     */
    public GraphSnapshot upsert(GraphSnapshot incoming) {
        String key = graphKey(incoming.getOntologyId(), incoming.getEnvCode());
        EnvGraph envGraph =
            graphs.computeIfAbsent(key, item -> new EnvGraph(incoming.getOntologyId(), incoming.getEnvCode()));
        envGraph.lock.writeLock().lock();
        try {
            envGraph.upsert(incoming);
            return envGraph.toSnapshot(incoming);
        } finally {
            envGraph.lock.writeLock().unlock();
        }
    }

    /**
     * Builds a merged snapshot without changing the loaded graph.
     *
     * @param incoming the incoming snapshot
     * @return merged snapshot
     */
    public GraphSnapshot mergeSnapshot(GraphSnapshot incoming) {
        EnvGraph existing = graphs.get(graphKey(incoming.getOntologyId(), incoming.getEnvCode()));
        if (existing == null) {
            EnvGraph envGraph = new EnvGraph(incoming.getOntologyId(), incoming.getEnvCode());
            envGraph.upsert(incoming);
            return envGraph.toSnapshot(incoming);
        }
        existing.lock.readLock().lock();
        try {
            EnvGraph envGraph = EnvGraph.fromSnapshot(existing.toSnapshot(null));
            envGraph.upsert(incoming);
            return envGraph.toSnapshot(incoming);
        } finally {
            existing.lock.readLock().unlock();
        }
    }

    /**
     * Gets a snapshot for the environment.
     *
     * @param envCode the envCode
     * @return the result
     */
    public Optional<GraphSnapshot> getSnapshot(String ontologyId, String envCode) {
        EnvGraph envGraph = graphs.get(graphKey(ontologyId, envCode));
        if (envGraph == null) {
            return Optional.empty();
        }
        envGraph.lock.readLock().lock();
        try {
            return Optional.of(envGraph.toSnapshot(null));
        } finally {
            envGraph.lock.readLock().unlock();
        }
    }

    /**
     * Counts loaded environment graphs under one ontology.
     *
     * @param ontologyId the ontologyId
     * @return graph count
     */
    public int countOntologySnapshots(String ontologyId) {
        String keyPrefix = ontologyId + "::";
        return (int) graphs.keySet().stream().filter(key -> key.startsWith(keyPrefix)).count();
    }

    /**
     * Lists loaded environment codes under one ontology.
     *
     * @param ontologyId the ontologyId
     * @return environment codes
     */
    public List<String> listEnvironmentCodes(String ontologyId) {
        String keyPrefix = ontologyId + "::";
        return graphs.keySet()
            .stream()
            .filter(key -> key.startsWith(keyPrefix))
            .map(key -> key.substring(keyPrefix.length()))
            .sorted()
            .toList();
    }

    /**
     * Deletes all environment graphs under one ontology.
     *
     * @param ontologyId the ontologyId
     * @return deleted graph count
     */
    public int deleteOntology(String ontologyId) {
        String keyPrefix = ontologyId + "::";
        List<String> keys = graphs.keySet().stream().filter(key -> key.startsWith(keyPrefix)).toList();
        keys.forEach(graphs::remove);
        return keys.size();
    }

    /**
     * Deletes one environment graph.
     *
     * @param ontologyId the ontologyId
     * @param envCode the envCode
     * @return true if graph existed
     */
    public boolean deleteSnapshot(String ontologyId, String envCode) {
        return graphs.remove(graphKey(ontologyId, envCode)) != null;
    }

    /**
     * Gets an entity.
     *
     * @param envCode the envCode
     * @param entityId the entityId
     * @return the result
     */
    public Optional<GraphEntity> getEntity(String ontologyId, String envCode, String entityId) {
        EnvGraph envGraph = graphs.get(graphKey(ontologyId, envCode));
        if (envGraph == null) {
            return Optional.empty();
        }
        envGraph.lock.readLock().lock();
        try {
            return Optional.ofNullable(envGraph.entities.get(entityId));
        } finally {
            envGraph.lock.readLock().unlock();
        }
    }

    /**
     * Queries a subgraph.
     *
     * @param envCode the envCode
     * @param entityId the entityId
     * @param maxHops the maxHops
     * @return the result
     */
    public Optional<GraphSnapshot> querySubgraph(String ontologyId, String envCode, String entityId, int maxHops) {
        EnvGraph envGraph = graphs.get(graphKey(ontologyId, envCode));
        if (envGraph == null) {
            return Optional.empty();
        }
        envGraph.lock.readLock().lock();
        try {
            if (!envGraph.entities.containsKey(entityId)) {
                return Optional.empty();
            }
            Set<String> selectedEntities = collectEntityIds(envGraph, entityId, maxHops);
            Set<String> selectedRelations = new LinkedHashSet<>();
            for (GraphRelation relation : envGraph.relations.values()) {
                if (selectedEntities.contains(relation.getFrom()) && selectedEntities.contains(relation.getTo())) {
                    selectedRelations.add(relation.getId());
                }
            }
            GraphSnapshot result = new GraphSnapshot();
            result.setOntologyId(ontologyId);
            result.setEnvCode(envCode);
            result.setSnapshotId(envGraph.snapshotId);
            result.setSchemaVersion(envGraph.schemaVersion);
            List<GraphEntity> entityList = selectedEntities.stream()
                .map(id -> envGraph.entities.get(id)).toList();
            List<GraphRelation> relationList = selectedRelations.stream()
                .map(id -> envGraph.relations.get(id)).toList();
            List<GraphObservation> observationList = envGraph.observations.values().stream()
                .filter(obs -> selectedEntities.contains(obs.getEntityId())).toList();
            result.setEntities(entityList);
            result.setRelations(relationList);
            result.setObservations(observationList);
            return Optional.of(result);
        } finally {
            envGraph.lock.readLock().unlock();
        }
    }

    /**
     * Queries a directional subgraph.
     *
     * @param ontologyId the ontologyId
     * @param envCode the envCode
     * @param entityId the entityId
     * @param upstreamHops the upstreamHops
     * @param downstreamHops the downstreamHops
     * @return the result
     */
    public Optional<GraphSnapshot> querySubgraph(String ontologyId, String envCode, String entityId, int upstreamHops,
        int downstreamHops) {
        EnvGraph envGraph = graphs.get(graphKey(ontologyId, envCode));
        if (envGraph == null) {
            return Optional.empty();
        }
        envGraph.lock.readLock().lock();
        try {
            if (!envGraph.entities.containsKey(entityId)) {
                return Optional.empty();
            }
            SubgraphSelection selection = collectDirectionalSubgraph(envGraph, entityId, upstreamHops, downstreamHops);
            GraphSnapshot result = new GraphSnapshot();
            result.setOntologyId(ontologyId);
            result.setEnvCode(envCode);
            result.setSnapshotId(envGraph.snapshotId);
            result.setSchemaVersion(envGraph.schemaVersion);
            List<GraphEntity> entityList = selection.entityIds().stream()
                .map(id -> envGraph.entities.get(id)).toList();
            List<GraphRelation> relationList = selection.relationIds().stream()
                .map(id -> envGraph.relations.get(id)).toList();
            List<GraphObservation> observationList = envGraph.observations.values().stream()
                .filter(obs -> selection.entityIds().contains(obs.getEntityId())).toList();
            result.setEntities(entityList);
            result.setRelations(relationList);
            result.setObservations(observationList);
            return Optional.of(result);
        } finally {
            envGraph.lock.readLock().unlock();
        }
    }

    /**
     * Finds the shortest relation path between two entities.
     *
     * @param envCode the envCode
     * @param fromEntityId the fromEntityId
     * @param toEntityId the toEntityId
     * @param maxHops the maxHops
     * @return the result
     */
    public Optional<GraphSnapshot> findPath(String ontologyId, String envCode, String fromEntityId, String toEntityId,
        int maxHops) {
        EnvGraph envGraph = graphs.get(graphKey(ontologyId, envCode));
        if (envGraph == null) {
            return Optional.empty();
        }
        envGraph.lock.readLock().lock();
        try {
            if (!envGraph.entities.containsKey(fromEntityId) || !envGraph.entities.containsKey(toEntityId)) {
                return Optional.empty();
            }
            PathSearchResult searchResult = searchPath(envGraph, fromEntityId, toEntityId, maxHops);
            if (!searchResult.isFound()) {
                return Optional.empty();
            }
            GraphSnapshot result = envGraph.toSnapshot(null);
            result.setEntities(searchResult.entityIds().stream().map(envGraph.entities::get).toList());
            result.setRelations(searchResult.relationIds().stream().map(envGraph.relations::get).toList());
            result.setObservations(result.getObservations()
                .stream()
                .filter(observation -> searchResult.entityIds().contains(observation.getEntityId()))
                .toList());
            return Optional.of(result);
        } finally {
            envGraph.lock.readLock().unlock();
        }
    }

    private Set<String> collectEntityIds(EnvGraph envGraph, String startId, int maxHops) {
        Set<String> visited = new LinkedHashSet<>();
        Map<String, Integer> distance = new HashMap<>();
        Queue<String> queue = new ArrayDeque<>();
        visited.add(startId);
        distance.put(startId, 0);
        queue.add(startId);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currentDistance = distance.get(current);
            if (currentDistance >= maxHops) {
                continue;
            }
            for (String relationId : envGraph.entityRelations.getOrDefault(current, Set.of())) {
                GraphRelation relation = envGraph.relations.get(relationId);
                if (relation == null) {
                    continue;
                }
                String next = relation.getFrom().equals(current) ? relation.getTo() : relation.getFrom();
                if (visited.add(next)) {
                    distance.put(next, currentDistance + 1);
                    queue.add(next);
                }
            }
        }
        return visited;
    }

    private SubgraphSelection collectDirectionalSubgraph(EnvGraph envGraph, String startId, int upstreamHops,
        int downstreamHops) {
        Set<String> selectedEntities = new LinkedHashSet<>();
        Set<String> selectedRelations = new LinkedHashSet<>();
        selectedEntities.add(startId);
        collectDirectionalEntityIds(envGraph, startId, upstreamHops, false, selectedEntities, selectedRelations);
        collectDirectionalEntityIds(envGraph, startId, downstreamHops, true, selectedEntities, selectedRelations);
        return new SubgraphSelection(selectedEntities, selectedRelations);
    }

    private void collectDirectionalEntityIds(EnvGraph envGraph, String startId, int maxHops, boolean downstream,
        Set<String> selectedEntities, Set<String> selectedRelations) {
        Map<String, Integer> distance = new HashMap<>();
        Queue<String> queue = new ArrayDeque<>();
        distance.put(startId, 0);
        queue.add(startId);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currentDistance = distance.get(current);
            if (currentDistance >= maxHops) {
                continue;
            }
            processDirectionalNeighbors(envGraph, current, downstream, currentDistance, distance, queue,
                selectedEntities, selectedRelations);
        }
    }

    private void processDirectionalNeighbors(EnvGraph envGraph, String current, boolean downstream,
        int currentDistance, Map<String, Integer> distance, Queue<String> queue, Set<String> selectedEntities,
        Set<String> selectedRelations) {
        for (String relationId : envGraph.entityRelations.getOrDefault(current, Set.of())) {
            GraphRelation relation = envGraph.relations.get(relationId);
            if (relation == null || !isDirectionalMatch(relation, current, downstream)) {
                continue;
            }
            String next = downstream ? relation.getTo() : relation.getFrom();
            selectedRelations.add(relationId);
            selectedEntities.add(next);
            if (!distance.containsKey(next)) {
                distance.put(next, currentDistance + 1);
                queue.add(next);
            }
        }
    }

    private boolean isDirectionalMatch(GraphRelation relation, String current, boolean downstream) {
        if (downstream) {
            return relation.getFrom().equals(current);
        }
        return relation.getTo().equals(current);
    }

    private String graphKey(String ontologyId, String envCode) {
        return ontologyId + "::" + envCode;
    }

    private PathSearchResult searchPath(EnvGraph envGraph, String fromEntityId, String toEntityId, int maxHops) {
        Map<String, Integer> distance = new HashMap<>();
        Map<String, String> previousEntity = new HashMap<>();
        Map<String, String> previousRelation = new HashMap<>();
        Queue<String> queue = new ArrayDeque<>();
        distance.put(fromEntityId, 0);
        queue.add(fromEntityId);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equals(toEntityId)) {
                return buildPath(fromEntityId, toEntityId, previousEntity, previousRelation);
            }
            int currentDistance = distance.get(current);
            if (currentDistance >= maxHops) {
                continue;
            }
            processPathNeighbors(envGraph, current, currentDistance, distance, previousEntity, previousRelation, queue);
        }
        return PathSearchResult.notFound();
    }

    private void processPathNeighbors(EnvGraph envGraph, String current, int currentDistance,
        Map<String, Integer> distance, Map<String, String> previousEntity, Map<String, String> previousRelation,
        Queue<String> queue) {
        for (String relationId : envGraph.entityRelations.getOrDefault(current, Set.of())) {
            GraphRelation relation = envGraph.relations.get(relationId);
            if (relation == null || !relation.getFrom().equals(current)) {
                continue;
            }
            String next = relation.getTo();
            if (distance.containsKey(next)) {
                continue;
            }
            distance.put(next, currentDistance + 1);
            previousEntity.put(next, current);
            previousRelation.put(next, relationId);
            queue.add(next);
        }
    }

    private PathSearchResult buildPath(String fromEntityId, String toEntityId, Map<String, String> previousEntity,
        Map<String, String> previousRelation) {
        List<String> entityIds = new ArrayList<>();
        List<String> relationIds = new ArrayList<>();
        String current = toEntityId;
        entityIds.add(current);
        while (!current.equals(fromEntityId)) {
            String relationId = previousRelation.get(current);
            String previous = previousEntity.get(current);
            if (relationId == null || previous == null) {
                return PathSearchResult.notFound();
            }
            relationIds.add(relationId);
            entityIds.add(previous);
            current = previous;
        }
        Collections.reverse(entityIds);
        Collections.reverse(relationIds);
        return new PathSearchResult(true, entityIds, relationIds);
    }

    /** Result of a BFS path search between two entities. */
    private record PathSearchResult(boolean isFound, List<String> entityIds, List<String> relationIds) {
        static PathSearchResult notFound() {
            return new PathSearchResult(false, List.of(), List.of());
        }
    }

    /** Selected entity and relation IDs for a directional subgraph. */
    private record SubgraphSelection(Set<String> entityIds, Set<String> relationIds) {
    }

    /** Internal graph representation for one ontology environment. */
    private static class EnvGraph {
        private final ReadWriteLock lock = new ReentrantReadWriteLock();

        private final String ontologyId;

        private final String envCode;

        private final Map<String, GraphEntity> entities = new LinkedHashMap<>();

        private final Map<String, GraphRelation> relations = new LinkedHashMap<>();

        private final Map<String, GraphObservation> observations = new LinkedHashMap<>();

        private final Map<String, Set<String>> entityRelations = new LinkedHashMap<>();

        private String schemaVersion = "1.0";

        private String snapshotId;

        private String generatedAt;

        private String sourceSystem;

        private Map<String, Object> metadata = new LinkedHashMap<>();

        EnvGraph(String ontologyId, String envCode) {
            this.ontologyId = ontologyId;
            this.envCode = envCode;
        }

        static EnvGraph fromSnapshot(GraphSnapshot snapshot) {
            EnvGraph envGraph = new EnvGraph(snapshot.getOntologyId(), snapshot.getEnvCode());
            envGraph.upsert(snapshot);
            return envGraph;
        }

        private static String firstNonBlank(String first, String second) {
            return first == null || first.isBlank() ? second : first;
        }

        void upsert(GraphSnapshot snapshot) {
            schemaVersion = firstNonBlank(snapshot.getSchemaVersion(), schemaVersion);
            snapshotId = firstNonBlank(snapshot.getSnapshotId(), snapshotId);
            generatedAt = firstNonBlank(snapshot.getGeneratedAt(), generatedAt);
            sourceSystem = firstNonBlank(snapshot.getSourceSystem(), sourceSystem);
            metadata = new LinkedHashMap<>(snapshot.getMetadata() != null ? snapshot.getMetadata() : Map.of());
            for (GraphEntity entity : snapshot.getEntities()) {
                entities.put(entity.getId(), entity);
            }
            for (GraphRelation relation : snapshot.getRelations()) {
                GraphRelation old = relations.put(relation.getId(), relation);
                if (old != null) {
                    removeRelationIndex(old);
                }
                addRelationIndex(relation);
            }
            for (GraphObservation observation : snapshot.getObservations()) {
                observations.put(observation.getId(), observation);
            }
        }

        GraphSnapshot toSnapshot(GraphSnapshot request) {
            GraphSnapshot snapshot = new GraphSnapshot();
            snapshot.setOntologyId(ontologyId);
            snapshot.setEnvCode(envCode);
            snapshot.setSchemaVersion(
                request == null ? schemaVersion : firstNonBlank(request.getSchemaVersion(), schemaVersion));
            snapshot.setSnapshotId(request == null ? snapshotId : firstNonBlank(request.getSnapshotId(), snapshotId));
            snapshot
                .setGeneratedAt(request == null ? generatedAt : firstNonBlank(request.getGeneratedAt(), generatedAt));
            snapshot.setSourceSystem(
                request == null ? sourceSystem : firstNonBlank(request.getSourceSystem(), sourceSystem));
            snapshot.setImportMode("UPSERT");
            snapshot.setMetadata(request == null ? metadata : request.getMetadata());
            snapshot.setEntities(entities.values().stream()
                .sorted(Comparator.comparing(GraphEntity::getId)).toList());
            snapshot.setRelations(relations.values().stream()
                .sorted(Comparator.comparing(GraphRelation::getId)).toList());
            snapshot.setObservations(observations.values().stream()
                .sorted(Comparator.comparing(GraphObservation::getId)).toList());
            return snapshot;
        }

        private void addRelationIndex(GraphRelation relation) {
            entityRelations.computeIfAbsent(relation.getFrom(), key -> new HashSet<>()).add(relation.getId());
            entityRelations.computeIfAbsent(relation.getTo(), key -> new HashSet<>()).add(relation.getId());
        }

        private void removeRelationIndex(GraphRelation relation) {
            removeRelationIndex(relation.getFrom(), relation.getId());
            removeRelationIndex(relation.getTo(), relation.getId());
        }

        private void removeRelationIndex(String entityId, String relationId) {
            Set<String> relationIds = entityRelations.get(entityId);
            if (relationIds == null) {
                return;
            }
            relationIds.remove(relationId);
            if (relationIds.isEmpty()) {
                entityRelations.remove(entityId);
            }
        }
    }
}
