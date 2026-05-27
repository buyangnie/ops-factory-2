/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.knowledgegraph.store;

import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.GraphSnapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Snapshot file store for knowledge graph.
 *
 * @author x00000000
 * @since 2026-05-20
 */
@Component
public class GraphSnapshotStore {
    private static final Logger log = LoggerFactory.getLogger(GraphSnapshotStore.class);

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private static final DateTimeFormatter FILE_TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private static final Pattern SAFE_PATH_SEGMENT = Pattern.compile("[A-Za-z0-9_.-]+");

    private final OperationIntelligenceProperties properties;

    /**
     * Constructs a GraphSnapshotStore.
     *
     * @param properties the operation intelligence properties
     */
    public GraphSnapshotStore(OperationIntelligenceProperties properties) {
        this.properties = properties;
    }

    /**
     * Resolves snapshot root directory.
     *
     * @return the result
     */
    public Path resolveRoot() {
        return properties.resolveKnowledgeGraphDataRoot();
    }

    /**
     * Saves one environment snapshot atomically.
     *
     * @param snapshot the snapshot
     */
    public void save(GraphSnapshot snapshot) {
        Path envDir = envDir(snapshot.getOntologyId(), snapshot.getEnvCode());
        Path tmp = null;
        try {
            Files.createDirectories(envDir);
            String fileName =
                "snapshot_" + OffsetDateTime.now().format(FILE_TS_FORMAT) + "_" + UUID.randomUUID() + ".json";
            tmp = envDir.resolve(fileName + ".tmp");
            Path target = envDir.resolve(fileName);
            MAPPER.writeValue(tmp.toFile(), snapshot);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            cleanup(envDir);
        } catch (IOException e) {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException deleteEx) {
                    log.warn("Failed to delete temp file {}: {}", tmp, deleteEx.getMessage());
                }
            }
            throw new IllegalStateException("Failed to save graph snapshot", e);
        }
    }

    /**
     * Loads latest snapshot for one environment.
     *
     * @param envCode the envCode
     * @return the result
     */
    public Optional<GraphSnapshot> loadLatest(String ontologyId, String envCode) {
        List<Path> files = listSnapshots(ontologyId, envCode);
        for (int index = files.size() - 1; index >= 0; index--) {
            Path file = files.get(index);
            try {
                return Optional.of(MAPPER.readValue(file.toFile(), GraphSnapshot.class));
            } catch (IOException e) {
                log.warn("Failed to load graph snapshot {}: {}", file, e.getMessage());
            }
        }
        return Optional.empty();
    }

    /**
     * Loads all latest snapshots.
     *
     * @return the result
     */
    public List<GraphSnapshot> loadLatestAll() {
        List<GraphSnapshot> snapshots = new ArrayList<>();
        Path root = resolveRoot();
        if (!Files.isDirectory(root)) {
            return snapshots;
        }
        try (Stream<Path> ontologyDirs = Files.list(root)) {
            ontologyDirs.filter(Files::isDirectory)
                .forEach(ontologyDir -> loadLatestEnvironments(ontologyDir).forEach(snapshots::add));
        } catch (IOException e) {
            log.warn("Failed to list graph snapshots: {}", e.getMessage());
        }
        return snapshots;
    }

    /**
     * Counts persisted environment snapshot directories under one ontology.
     *
     * @param ontologyId the ontologyId
     * @return snapshot environment count
     */
    public int countOntologySnapshots(String ontologyId) {
        Path ontologyDir = ontologyDir(ontologyId);
        if (!Files.isDirectory(ontologyDir)) {
            return 0;
        }
        try (Stream<Path> envDirs = Files.list(ontologyDir)) {
            return (int) envDirs.filter(Files::isDirectory).filter(envDir -> !listSnapshots(envDir).isEmpty()).count();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to count graph snapshots", e);
        }
    }

    /**
     * Lists persisted environment codes under one ontology.
     *
     * @param ontologyId the ontologyId
     * @return environment codes
     */
    public List<String> listEnvironmentCodes(String ontologyId) {
        Path ontologyDir = ontologyDir(ontologyId);
        if (!Files.isDirectory(ontologyDir)) {
            return List.of();
        }
        try (Stream<Path> envDirs = Files.list(ontologyDir)) {
            return envDirs.filter(Files::isDirectory)
                .filter(envDir -> !listSnapshots(envDir).isEmpty())
                .map(envDir -> envDir.getFileName().toString())
                .sorted()
                .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list graph snapshot environments", e);
        }
    }

    /**
     * Deletes all persisted snapshots under one ontology.
     *
     * @param ontologyId the ontologyId
     */
    public void deleteOntology(String ontologyId) {
        deleteRecursively(ontologyDir(ontologyId));
    }

    /**
     * Deletes one persisted environment snapshot directory.
     *
     * @param ontologyId the ontologyId
     * @param envCode the envCode
     */
    public void deleteSnapshot(String ontologyId, String envCode) {
        deleteRecursively(envDir(ontologyId, envCode));
    }

    private void cleanup(Path envDir) throws IOException {
        int retention = Math.max(properties.getKnowledgeGraph().getSnapshotRetention(), 1);
        List<Path> files = listSnapshots(envDir);
        int removable = files.size() - retention;
        for (int index = 0; index < removable; index++) {
            Files.deleteIfExists(files.get(index));
        }
    }

    private List<GraphSnapshot> loadLatestEnvironments(Path ontologyDir) {
        List<GraphSnapshot> snapshots = new ArrayList<>();
        String ontologyId = ontologyDir.getFileName().toString();
        try (Stream<Path> envDirs = Files.list(ontologyDir)) {
            envDirs.filter(Files::isDirectory)
                .forEach(envDir -> loadLatest(ontologyId, envDir.getFileName().toString()).ifPresent(snapshots::add));
        } catch (IOException e) {
            log.warn("Failed to list graph snapshot environments under {}: {}", ontologyDir, e.getMessage());
        }
        return snapshots;
    }

    private List<Path> listSnapshots(String ontologyId, String envCode) {
        return listSnapshots(envDir(ontologyId, envCode));
    }

    private List<Path> listSnapshots(Path envDir) {
        if (!Files.isDirectory(envDir)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(envDir)) {
            return paths.filter(path -> path.getFileName().toString().startsWith("snapshot_"))
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .toList();
        } catch (IOException e) {
            log.warn("Failed to list snapshot files under {}: {}", envDir, e.getMessage());
            return List.of();
        }
    }

    private Path envDir(String ontologyId, String envCode) {
        Path root = resolveRoot();
        Path resolved = root.resolve(safePathSegment(ontologyId, "ontologyId"))
            .resolve(safePathSegment(envCode, "envCode"))
            .normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Graph snapshot path is outside data root");
        }
        return resolved;
    }

    private Path ontologyDir(String ontologyId) {
        Path root = resolveRoot();
        Path resolved = root.resolve(safePathSegment(ontologyId, "ontologyId")).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Graph ontology snapshot path is outside data root");
        }
        return resolved;
    }

    private String safePathSegment(String value, String fieldName) {
        if (value == null || !SAFE_PATH_SEGMENT.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + " contains unsupported path characters");
        }
        return value;
    }

    private void deleteRecursively(Path root) {
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
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete graph snapshots", e);
        }
    }
}
