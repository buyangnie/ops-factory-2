/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.knowledgegraph.service;

import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.EntityTypeDefinition;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.GraphEntity;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.GraphOntology;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.GraphRelation;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.GraphSnapshot;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.RelationTypeDefinition;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** Unit tests for GraphSchemaRegistry ontology registration and validation. */
class GraphSchemaRegistryTest {
    @Test
    void register_allowsSameRelationTypeWithDifferentEndpoints() {
        GraphSchemaRegistry registry = new GraphSchemaRegistry();
        GraphOntology ontology = ontology("multi-endpoint-relation-v1",
            List.of(relationType("calls", List.of("BusinessService"), List.of("Cluster")),
                relationType("calls", List.of("Cluster"), List.of("Cluster"))));

        GraphOntology registered = registry.register(ontology);

        Assertions.assertEquals("multi-endpoint-relation-v1", registered.getOntologyId());
        Assertions.assertEquals(2, registered.getRelationTypes().size());
    }

    @Test
    void register_rejectsSameRelationTypeWithSameEndpoints() {
        GraphSchemaRegistry registry = new GraphSchemaRegistry();
        GraphOntology ontology = ontology("duplicate-relation-v1",
            List.of(relationType("calls", List.of("BusinessService"), List.of("Cluster")),
                relationType("calls", List.of("BusinessService"), List.of("Cluster"))));

        ResponseStatusException exception =
            Assertions.assertThrows(ResponseStatusException.class, () -> registry.register(ontology));

        Assertions.assertTrue(exception.getReason().contains("Duplicate relation definition"));
    }

    @Test
    void validate_matchesAnyEndpointDefinitionForSameRelationType() {
        GraphSchemaRegistry registry = new GraphSchemaRegistry();
        GraphOntology ontology = ontology("runtime-topology-v1",
            List.of(relationType("calls", List.of("BusinessService"), List.of("Cluster")),
                relationType("calls", List.of("Cluster"), List.of("Cluster"))));
        registry.register(ontology);

        GraphSnapshot snapshot = snapshot("runtime-topology-v1",
            List.of(entity("business-order", "BusinessService"), entity("cluster-order", "Cluster"),
                entity("cluster-db", "Cluster")),
            List.of(relation("rel-business-cluster", "calls", "business-order", "cluster-order"),
                relation("rel-cluster-cluster", "calls", "cluster-order", "cluster-db")));

        Assertions.assertDoesNotThrow(() -> registry.validate(snapshot));
    }

    private GraphOntology ontology(String ontologyId, List<RelationTypeDefinition> relationTypes) {
        GraphOntology ontology = new GraphOntology();
        ontology.setOntologyId(ontologyId);
        ontology.setName(ontologyId);
        ontology.setSourceSystem("test");
        ontology.setEntityTypes(List.of(entityType("BusinessService"), entityType("Cluster")));
        ontology.setRelationTypes(relationTypes);
        return ontology;
    }

    private EntityTypeDefinition entityType(String type) {
        EntityTypeDefinition definition = new EntityTypeDefinition();
        definition.setType(type);
        definition.setRequiredProperties(List.of());
        return definition;
    }

    private RelationTypeDefinition relationType(String type, List<String> from, List<String> to) {
        RelationTypeDefinition definition = new RelationTypeDefinition();
        definition.setType(type);
        definition.setLayer("runtime");
        definition.setFrom(from);
        definition.setTo(to);
        return definition;
    }

    private GraphSnapshot snapshot(String ontologyId, List<GraphEntity> entities, List<GraphRelation> relations) {
        GraphSnapshot snapshot = new GraphSnapshot();
        snapshot.setOntologyId(ontologyId);
        snapshot.setEnvCode("prod");
        snapshot.setSourceSystem("test");
        snapshot.setImportMode("UPSERT");
        snapshot.setEntities(entities);
        snapshot.setRelations(relations);
        return snapshot;
    }

    private GraphEntity entity(String id, String type) {
        GraphEntity entity = new GraphEntity();
        entity.setId(id);
        entity.setType(type);
        entity.setName(id);
        entity.setProperties(Map.of());
        return entity;
    }

    private GraphRelation relation(String id, String type, String from, String to) {
        GraphRelation relation = new GraphRelation();
        relation.setId(id);
        relation.setType(type);
        relation.setFrom(from);
        relation.setTo(to);
        return relation;
    }
}
