/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.knowledgegraph.store;

import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.GraphOntology;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Ontology file store for knowledge graph.
 *
 * @author x00000000
 * @since 2026-05-22
 */
@Component
public class GraphOntologyStore {
    private static final Logger log = LoggerFactory.getLogger(GraphOntologyStore.class);

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private static final Pattern SAFE_PATH_SEGMENT = Pattern.compile("[A-Za-z0-9_.-]+");

    private final OperationIntelligenceProperties properties;

    /**
     * Constructs a GraphOntologyStore.
     *
     * @param properties the operation intelligence properties
     */
    public GraphOntologyStore(OperationIntelligenceProperties properties) {
        this.properties = properties;
    }

    /**
     * Saves an ontology.
     *
     * @param ontology the ontology
     */
    public void save(GraphOntology ontology) {
        try {
            Path root = resolveRoot();
            Files.createDirectories(root);
            MAPPER.writeValue(ontologyPath(ontology.getOntologyId()).toFile(), ontology);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save graph ontology", e);
        }
    }

    /**
     * Deletes a persisted ontology.
     *
     * @param ontologyId the ontologyId
     */
    public void delete(String ontologyId) {
        try {
            Files.deleteIfExists(ontologyPath(ontologyId));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete graph ontology", e);
        }
    }

    /**
     * Loads all ontologies.
     *
     * @return ontologies
     */
    public List<GraphOntology> loadAll() {
        List<GraphOntology> ontologies = new ArrayList<>();
        Path root = resolveRoot();
        if (!Files.isDirectory(root)) {
            return ontologies;
        }
        try (Stream<Path> paths = Files.list(root)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                .forEach(path -> load(path, ontologies));
        } catch (IOException e) {
            log.warn("Failed to list graph ontologies: {}", e.getMessage());
        }
        return ontologies;
    }

    private void load(Path path, List<GraphOntology> ontologies) {
        try {
            ontologies.add(MAPPER.readValue(path.toFile(), GraphOntology.class));
        } catch (IOException e) {
            log.warn("Failed to load graph ontology {}: {}", path, e.getMessage());
        }
    }

    private Path resolveRoot() {
        return properties.resolveKnowledgeGraphDataRoot()
            .resolve("_ontologies")
            .toAbsolutePath()
            .normalize();
    }

    private Path ontologyPath(String ontologyId) {
        Path root = resolveRoot();
        Path resolved = root.resolve(safePathSegment(ontologyId, "ontologyId") + ".json").normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Graph ontology path is outside data root");
        }
        return resolved;
    }

    private String safePathSegment(String value, String fieldName) {
        if (value == null || !SAFE_PATH_SEGMENT.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + " contains unsupported path characters");
        }
        return value;
    }
}
