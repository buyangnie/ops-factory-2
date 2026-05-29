/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.callchainsubgraph.store;

import com.huawei.opsfactory.operationintelligence.callchainsubgraph.model.CallChainSubgraphResult;
import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Short-term file store for generated call chain entity subgraphs.
 *
 * @author x00000000
 * @since 2026-05-27
 */
@Component
public class CallChainSubgraphStore {
    private static final Logger log = LoggerFactory.getLogger(CallChainSubgraphStore.class);

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private static final Pattern SAFE_PATH_SEGMENT = Pattern.compile("[A-Za-z0-9_.-]+");

    private final OperationIntelligenceProperties properties;

    /**
     * Constructs a CallChainSubgraphStore.
     *
     * @param properties the operation intelligence properties
     */
    public CallChainSubgraphStore(OperationIntelligenceProperties properties) {
        this.properties = properties;
    }

    /**
     * Saves a generated subgraph atomically.
     *
     * @param result the result
     */
    public void save(CallChainSubgraphResult result) {
        Path target = filePath(result.getSubgraphId());
        Path tempFile = target.resolveSibling(target.getFileName().toString() + ".tmp");
        try {
            Files.createDirectories(resolveRoot());
            cleanupExpired();
            try (OutputStream outputStream = Files.newOutputStream(tempFile)) {
                MAPPER.writeValue(outputStream, result);
            }
            Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            cleanupOverflow();
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException deleteEx) {
                log.warn("Failed to delete temp call chain subgraph file {}: {}", tempFile, deleteEx.getMessage());
            }
            throw new CallChainSubgraphStoreException("Failed to save call chain subgraph", e);
        }
    }

    /**
     * Loads one generated subgraph by id.
     *
     * @param subgraphId the subgraphId
     * @return the result
     */
    public Optional<CallChainSubgraphResult> load(String subgraphId) {
        Path file = filePath(subgraphId);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            CallChainSubgraphResult result = MAPPER.readValue(file.toFile(), CallChainSubgraphResult.class);
            if (isExpired(result)) {
                Files.deleteIfExists(file);
                return Optional.empty();
            }
            return Optional.of(result);
        } catch (IOException e) {
            throw new CallChainSubgraphStoreException("Failed to load call chain subgraph", e);
        }
    }

    /**
     * Lists all non-expired generated subgraphs.
     *
     * @return the results
     */
    public List<CallChainSubgraphResult> list() {
        List<Path> files = listSubgraphFiles();
        List<CallChainSubgraphResult> results = new java.util.ArrayList<>();
        for (int index = files.size() - 1; index >= 0; index--) {
            Path file = files.get(index);
            try {
                CallChainSubgraphResult result = MAPPER.readValue(file.toFile(), CallChainSubgraphResult.class);
                if (isExpired(result)) {
                    Files.deleteIfExists(file);
                    continue;
                }
                results.add(result);
            } catch (IOException e) {
                log.warn("Failed to read call chain subgraph file {}: {}", file, e.getMessage());
            }
        }
        return results;
    }

    /**
     * Cleans expired subgraphs.
     */
    public void cleanupExpired() {
        List<Path> files = listSubgraphFiles();
        for (Path file : files) {
            try {
                CallChainSubgraphResult result = MAPPER.readValue(file.toFile(), CallChainSubgraphResult.class);
                if (isExpired(result)) {
                    Files.deleteIfExists(file);
                }
            } catch (IOException e) {
                log.warn("Failed to inspect call chain subgraph file {}: {}", file, e.getMessage());
            }
        }
    }

    /**
     * Resolves the storage root.
     *
     * @return the root path
     */
    public Path resolveRoot() {
        return properties.resolveKnowledgeGraphDataRoot()
            .resolve(properties.getKnowledgeGraph().getCallChainSubgraphDir())
            .normalize();
    }

    private boolean isExpired(CallChainSubgraphResult result) {
        String expiresAt = result.getExpiresAt();
        if (expiresAt == null || expiresAt.isBlank()) {
            return false;
        }
        return OffsetDateTime.parse(expiresAt).isBefore(OffsetDateTime.now());
    }

    private void cleanupOverflow() throws IOException {
        int retention = Math.max(properties.getKnowledgeGraph().getCallChainSubgraphRetention(), 1);
        List<Path> files = listSubgraphFiles();
        int removableCount = files.size() - retention;
        for (int index = 0; index < removableCount; index++) {
            Files.deleteIfExists(files.get(index));
        }
    }

    private List<Path> listSubgraphFiles() {
        Path root = resolveRoot();
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(root)) {
            return paths.filter(path -> path.getFileName().toString().startsWith("subgraph_"))
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .sorted(Comparator.comparing(this::lastModifiedSafe))
                .toList();
        } catch (IOException e) {
            throw new CallChainSubgraphStoreException("Failed to list call chain subgraphs", e);
        }
    }

    private long lastModifiedSafe(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return Long.MIN_VALUE;
        }
    }

    private Path filePath(String subgraphId) {
        return resolveRoot().resolve("subgraph_" + safePathSegment(subgraphId, "subgraphId") + ".json").normalize();
    }

    private String safePathSegment(String value, String fieldName) {
        if (value == null || !SAFE_PATH_SEGMENT.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + " contains unsupported path characters");
        }
        return value;
    }
}
