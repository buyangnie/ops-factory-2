/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.knowledgegraph.service;

import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.EntityTypeDefinition;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.GraphEntity;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.GraphObservation;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.GraphOntology;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.GraphRelation;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.GraphSnapshot;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.RelationTypeDefinition;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Runtime ontology registry and graph schema validator.
 *
 * @author x00000000
 * @since 2026-05-22
 */
@Component
public class GraphSchemaRegistry {
    public static final String DEFAULT_ONTOLOGY_ID = "b2b-callchain-v1";

    private static final String LINE_SEPARATOR = System.lineSeparator();

    private static final String WILDCARD_TYPE = "*";

    private final Map<String, GraphOntology> ontologies = new ConcurrentHashMap<>();

    /**
     * Registers or replaces an ontology.
     *
     * @param ontology the ontology
     * @return registered ontology
     */
    public GraphOntology register(GraphOntology ontology) {
        validateOntology(ontology);
        ontologies.put(ontology.getOntologyId(), ontology);
        return ontology;
    }

    /**
     * Lists registered ontologies.
     *
     * @return ontologies
     */
    public List<GraphOntology> listOntologies() {
        return ontologies.values().stream().sorted(Comparator.comparing(GraphOntology::getOntologyId)).toList();
    }

    /**
     * Gets an ontology by id.
     *
     * @param ontologyId the ontologyId
     * @return the ontology
     */
    public GraphOntology getOntology(String ontologyId) {
        String resolvedOntologyId = resolveOntologyId(ontologyId);
        GraphOntology ontology = ontologies.get(resolvedOntologyId);
        if (ontology == null) {
            throw badRequest("Ontology does not exist: " + resolvedOntologyId);
        }
        return ontology;
    }

    /**
     * Deletes an ontology from runtime registry.
     *
     * @param ontologyId the ontologyId
     * @return deleted ontology
     */
    public GraphOntology deleteOntology(String ontologyId) {
        String resolvedOntologyId = resolveOntologyId(ontologyId);
        GraphOntology ontology = ontologies.remove(resolvedOntologyId);
        if (ontology == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ontology does not exist: " + resolvedOntologyId);
        }
        return ontology;
    }

    /**
     * Validates a graph snapshot against its ontology.
     *
     * @param snapshot the snapshot
     */
    public void validate(GraphSnapshot snapshot) {
        requireText(snapshot.getOntologyId(), "ontologyId");
        requireText(snapshot.getEnvCode(), "envCode");
        requireText(snapshot.getSourceSystem(), "sourceSystem");
        if (!"UPSERT".equals(snapshot.getImportMode())) {
            throw badRequest("Only UPSERT importMode is supported");
        }
        GraphOntology ontology = getOntology(snapshot.getOntologyId());
        Map<String, EntityTypeDefinition> entityTypes = entityTypeMap(ontology);
        Map<String, List<RelationTypeDefinition>> relationTypes = relationTypeMap(ontology);
        for (GraphEntity entity : snapshot.getEntities()) {
            validateEntity(entity, entityTypes);
        }
        for (GraphRelation relation : snapshot.getRelations()) {
            validateRelation(snapshot, relation, relationTypes);
        }
        for (GraphObservation observation : snapshot.getObservations()) {
            validateObservation(snapshot, observation);
        }
    }

    /**
     * Exports an ontology as schema DSL.
     *
     * @param ontologyId the ontologyId
     * @return schema DSL
     */
    public String exportSchemaDsl(String ontologyId) {
        GraphOntology ontology = getOntology(ontologyId);
        List<String> lines = new ArrayList<>();
        lines.add("ontologyId: \"" + ontology.getOntologyId() + "\"");
        lines.add("schemaVersion: \"" + safeVersion(ontology.getVersion()) + "\"");
        lines.add("entityTypes:");
        for (EntityTypeDefinition entityType : ontology.getEntityTypes()) {
            lines.add("  - type: \"" + entityType.getType() + "\"");
            lines.add("    requiredProperties:");
            for (String property : entityType.getRequiredProperties()) {
                lines.add("      - \"" + property + "\"");
            }
            lines.add("    optionalProperties:");
            for (String property : entityType.getOptionalProperties()) {
                lines.add("      - \"" + property + "\"");
            }
        }
        lines.add("relationTypes:");
        for (RelationTypeDefinition relationType : ontology.getRelationTypes()) {
            lines.add("  - type: \"" + relationType.getType() + "\"");
            lines.add("    layer: \"" + nullToBlank(relationType.getLayer()) + "\"");
            lines.add("    from:");
            for (String fromType : relationType.getFrom()) {
                lines.add("      - \"" + fromType + "\"");
            }
            lines.add("    to:");
            for (String toType : relationType.getTo()) {
                lines.add("      - \"" + toType + "\"");
            }
        }
        lines.add("");
        return String.join(LINE_SEPARATOR, lines);
    }

    private void validateOntology(GraphOntology ontology) {
        requireText(ontology.getOntologyId(), "ontologyId");
        if (ontology.getEntityTypes().isEmpty()) {
            throw badRequest("entityTypes is required");
        }
        if (ontology.getRelationTypes().isEmpty()) {
            throw badRequest("relationTypes is required");
        }
        Map<String, EntityTypeDefinition> entityTypes = entityTypeMap(ontology);
        if (entityTypes.size() != ontology.getEntityTypes().size()) {
            throw badRequest("Duplicate entity type in ontology: " + ontology.getOntologyId());
        }
        Set<String> relationSignatures = new HashSet<>();
        for (RelationTypeDefinition relationType : ontology.getRelationTypes()) {
            requireText(relationType.getType(), "relationType.type");
            if (relationType.getFrom().isEmpty() || relationType.getTo().isEmpty()) {
                throw badRequest("Relation endpoint types are required: " + relationType.getType());
            }
            if (!relationSignatures.add(relationSignature(relationType))) {
                throw badRequest("Duplicate relation definition in ontology: " + ontology.getOntologyId());
            }
            validateEndpointTypes(entityTypes, relationType.getFrom(), relationType.getType());
            validateEndpointTypes(entityTypes, relationType.getTo(), relationType.getType());
        }
    }

    private void validateEndpointTypes(Map<String, EntityTypeDefinition> entityTypes, List<String> endpointTypes,
        String relationType) {
        for (String endpointType : endpointTypes) {
            if (!WILDCARD_TYPE.equals(endpointType) && !entityTypes.containsKey(endpointType)) {
                throw badRequest("Unknown endpoint type " + endpointType + " in relation " + relationType);
            }
        }
    }

    private void validateEntity(GraphEntity entity, Map<String, EntityTypeDefinition> entityTypes) {
        requireText(entity.getId(), "entity.id");
        requireText(entity.getType(), "entity.type");
        EntityTypeDefinition entityType = entityTypes.get(entity.getType());
        if (entityType == null) {
            throw badRequest("Unsupported entity type: " + entity.getType());
        }
        for (String propertyName : entityType.getRequiredProperties()) {
            requireProperty(entity, propertyName);
        }
    }

    private void validateRelation(GraphSnapshot snapshot, GraphRelation relation,
        Map<String, List<RelationTypeDefinition>> relationTypes) {
        requireText(relation.getId(), "relation.id");
        requireText(relation.getType(), "relation.type");
        requireText(relation.getFrom(), "relation.from");
        requireText(relation.getTo(), "relation.to");
        List<RelationTypeDefinition> relationTypeDefinitions = relationTypes.get(relation.getType());
        if (relationTypeDefinitions == null || relationTypeDefinitions.isEmpty()) {
            throw badRequest("Unsupported relation type: " + relation.getType());
        }
        GraphEntity from = findEntity(snapshot, relation.getFrom());
        GraphEntity to = findEntity(snapshot, relation.getTo());
        if (from == null || to == null) {
            throw badRequest("Relation endpoint does not exist: " + relation.getId());
        }
        boolean hasMatchedEndpointDefinition = relationTypeDefinitions.stream()
            .anyMatch(relationType -> matchesEndpointType(relationType.getFrom(), from.getType())
                && matchesEndpointType(relationType.getTo(), to.getType()));
        if (!hasMatchedEndpointDefinition) {
            throw badRequest("Relation endpoint type mismatch: " + relation.getId());
        }
    }

    private void validateObservation(GraphSnapshot snapshot, GraphObservation observation) {
        requireText(observation.getId(), "observation.id");
        requireText(observation.getEntityId(), "observation.entityId");
        requireText(observation.getObservedAt(), "observation.observedAt");
        if (findEntity(snapshot, observation.getEntityId()) == null) {
            throw badRequest("Observation entity does not exist: " + observation.getId());
        }
    }

    private boolean matchesEndpointType(List<String> allowedTypes, String actualType) {
        return allowedTypes.contains(WILDCARD_TYPE) || allowedTypes.contains(actualType);
    }

    private GraphEntity findEntity(GraphSnapshot snapshot, String entityId) {
        return snapshot.getEntities()
            .stream()
            .filter(entity -> entityId.equals(entity.getId()))
            .findFirst()
            .orElse(null);
    }

    private Map<String, EntityTypeDefinition> entityTypeMap(GraphOntology ontology) {
        return ontology.getEntityTypes()
            .stream()
            .collect(Collectors.toMap(EntityTypeDefinition::getType, item -> item, (first, second) -> first,
                LinkedHashMap::new));
    }

    private Map<String, List<RelationTypeDefinition>> relationTypeMap(GraphOntology ontology) {
        return ontology.getRelationTypes()
            .stream()
            .collect(Collectors.groupingBy(RelationTypeDefinition::getType, LinkedHashMap::new, Collectors.toList()));
    }

    private String relationSignature(RelationTypeDefinition relationType) {
        return String.join("::", relationType.getType(), normalizedEndpointTypes(relationType.getFrom()),
            normalizedEndpointTypes(relationType.getTo()));
    }

    private String normalizedEndpointTypes(List<String> endpointTypes) {
        return endpointTypes.stream().sorted().collect(Collectors.joining(","));
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw badRequest(fieldName + " is required");
        }
    }

    private void requireProperty(GraphEntity entity, String propertyName) {
        Map<String, Object> properties = entity.getProperties();
        Object value = properties != null ? properties.get(propertyName) : null;
        if (value == null || value.toString().isBlank()) {
            throw badRequest(entity.getType() + "." + propertyName + " is required");
        }
    }

    private String resolveOntologyId(String ontologyId) {
        return ontologyId == null || ontologyId.isBlank() ? DEFAULT_ONTOLOGY_ID : ontologyId;
    }

    private String safeVersion(String version) {
        return version == null || version.isBlank() ? "1.0" : version;
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

}
