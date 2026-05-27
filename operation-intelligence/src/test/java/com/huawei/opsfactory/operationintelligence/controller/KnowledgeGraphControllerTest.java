/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.controller;

import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.GraphSnapshot;
import com.huawei.opsfactory.operationintelligence.knowledgegraph.service.KnowledgeGraphService;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Integration tests for KnowledgeGraph REST API endpoints. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class KnowledgeGraphControllerTest {
    private static final String SECRET_KEY = "kg-secret";

    private static final Path DATA_ROOT = createTempDataRoot();

    @Autowired
    private MockMvc mockMvc;

    private WebTestClient webTestClient;

    @Autowired
    private KnowledgeGraphService knowledgeGraphService;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("operation-intelligence.secret-key", () -> SECRET_KEY);
        registry.add("operation-intelligence.data-root", DATA_ROOT::toString);
        registry.add("operation-intelligence.qos.enabled", () -> "false");
        registry.add("operation-intelligence.knowledge-graph.enabled", () -> "true");
        registry.add("operation-intelligence.knowledge-graph.max-hops", () -> "4");
        registry.add("operation-intelligence.knowledge-graph.snapshot-retention", () -> "3");
    }

    @AfterAll
    static void tearDown() throws IOException {
        deleteRecursively(DATA_ROOT);
    }

    private static Path createTempDataRoot() {
        try {
            return Files.createTempDirectory("operation-intelligence-kg-test-");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create test data root", e);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
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
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        deleteRecursively(DATA_ROOT);
        Files.createDirectories(DATA_ROOT);
        webTestClient = MockMvcWebTestClient.bindTo(mockMvc).build();
    }

    @Test
    void graphEndpoints_requireSecret() {
        webTestClient.get()
            .uri("/operation-intelligence/graph/entities/biz-prod-604015020?envCode=prod")
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }

    @Test
    void importGraph_upsertsB2BCallChainAndPersistsSnapshot() throws IOException {
        importFixture();

        webTestClient.get()
            .uri("/operation-intelligence/graph/entities/svc-prod-bes-business-common-sysparambs?envCode=prod")
            .header("x-secret-key", SECRET_KEY)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(true)
            .jsonPath("$.result.type")
            .isEqualTo("Service")
            .jsonPath("$.result.properties.serviceName")
            .isEqualTo("bes.business.common.SysParamBS")
            .jsonPath("$.result.properties.operations[0].operationName")
            .isEqualTo("querySysParams");

        Path snapshotDir = DATA_ROOT.resolve("knowledge-graph").resolve("b2b-callchain-v1").resolve("prod");
        try (Stream<Path> paths = Files.list(snapshotDir)) {
            long snapshotCount = paths.filter(path -> path.getFileName().toString().endsWith(".json")).count();
            Assertions.assertEquals(1, snapshotCount);
        }
    }

    @Test
    void importGraph_acceptsLargeEntityJsonPayload() {
        importB2bOntology();
        String largeDescription = "large-import-payload-".repeat(20000);
        Map<String,
            Object> body = Map.of("ontologyId", "b2b-callchain-v1", "envCode", "large", "schemaVersion", "1.0",
                "sourceSystem", "large-import-test", "importMode", "UPSERT", "entities",
                List.of(entity("svc-large-import", "Service", "LargeImportService",
                    Map.of("serviceName", "LargeImportService", "description", largeDescription))));

        webTestClient.post()
            .uri("/operation-intelligence/graph/admin/import")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.result.entityCount")
            .isEqualTo(1);
    }

    @Test
    void importGraph_supportsIncrementalRelationsReferencingExistingEntities() throws IOException {
        importB2bOntology();
        Map<String, Object> entitiesOnly = Map.of("ontologyId", "b2b-callchain-v1", "envCode", "incremental",
            "schemaVersion", "1.0", "sourceSystem", "incremental-test", "importMode", "UPSERT", "entities",
            List.of(
                entity("svc-incremental", "Service", "IncrementalService", Map.of("serviceName", "IncrementalService")),
                entity("cluster-incremental", "Cluster", "IncrementalCluster",
                    Map.of("clusterName", "IncrementalCluster"))));
        webTestClient.post()
            .uri("/operation-intelligence/graph/admin/import")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(entitiesOnly)
            .exchange()
            .expectStatus()
            .isOk();

        Map<String,
            Object> relationOnly = Map.of("ontologyId", "b2b-callchain-v1", "envCode", "incremental", "schemaVersion",
                "1.0", "sourceSystem", "incremental-test", "importMode", "UPSERT", "relations", List
                    .of(relation("rel-incremental-deployed", "deployed_in", "svc-incremental", "cluster-incremental")));
        webTestClient.post()
            .uri("/operation-intelligence/graph/admin/import")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(relationOnly)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.result.entityCount")
            .isEqualTo(2)
            .jsonPath("$.result.relationCount")
            .isEqualTo(1);

        webTestClient.post()
            .uri("/operation-intelligence/graph/subgraph")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("envCode", "incremental", "entityId", "svc-incremental", "maxHops", 1))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.result.relations[0].id")
            .isEqualTo("rel-incremental-deployed");

        Path snapshotDir = DATA_ROOT.resolve("knowledge-graph").resolve("b2b-callchain-v1").resolve("incremental");
        try (Stream<Path> paths = Files.list(snapshotDir)) {
            long snapshotCount = paths.filter(path -> path.getFileName().toString().endsWith(".json")).count();
            Assertions.assertEquals(2, snapshotCount);
        }
    }

    @Test
    void importGraph_rejectsUnsafeEnvironmentPath() {
        Map<String,
            Object> body = Map.of("ontologyId", "b2b-callchain-v1", "envCode", "../escape", "schemaVersion", "1.0",
                "sourceSystem", "path-test", "importMode", "UPSERT", "entities", List.of(
                    entity("svc-path-test", "Service", "PathTestService", Map.of("serviceName", "PathTestService"))));

        webTestClient.post()
            .uri("/operation-intelligence/graph/admin/import")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus()
            .isBadRequest();

        Assertions.assertFalse(Files.exists(DATA_ROOT.resolve("escape")));
    }

    @Test
    void querySubgraph_supportsFourHops() throws IOException {
        importFixture();

        webTestClient.post()
            .uri("/operation-intelligence/graph/subgraph")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("envCode", "prod", "entityId", "svc-prod-bes-business-common-sysparambs", "maxHops", 4))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.result.entities.length()")
            .isEqualTo(3)
            .jsonPath("$.result.relations[0].type")
            .isEqualTo("deployed_in")
            .jsonPath("$.result.observations.length()")
            .isEqualTo(0);
    }

    @Test
    void querySubgraph_supportsDirectionalHops() throws IOException {
        importFixture();

        webTestClient.post()
            .uri("/operation-intelligence/graph/subgraph")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                Map.of("envCode", "prod", "entityId", "cluster-prod-rsp", "upstreamHops", 1, "downstreamHops", 0))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.result.entities.length()")
            .isEqualTo(3)
            .jsonPath("$.result.relations.length()")
            .isEqualTo(2);

        webTestClient.post()
            .uri("/operation-intelligence/graph/subgraph")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                Map.of("envCode", "prod", "entityId", "cluster-prod-rsp", "upstreamHops", 0, "downstreamHops", 1))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.result.entities.length()")
            .isEqualTo(1)
            .jsonPath("$.result.relations.length()")
            .isEqualTo(0);
    }

    @Test
    void querySubgraph_traversesMultipleDownstreamHops() {
        importResourceOntologyAndSnapshot();

        webTestClient.post()
            .uri("/operation-intelligence/graph/subgraph")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("ontologyId", "huawei-mo-resource-v1", "envCode", "prod", "entityId", "app-order",
                "upstreamHops", 0, "downstreamHops", 3))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.result.entities.length()")
            .isEqualTo(4)
            .jsonPath("$.result.relations.length()")
            .isEqualTo(3);
    }

    @Test
    void getResourceTree_groupsEntitiesByType() throws IOException {
        importFixture();

        webTestClient.get()
            .uri("/operation-intelligence/graph/resources/tree?envCode=prod")
            .header("x-secret-key", SECRET_KEY)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(true)
            .jsonPath("$.result.total")
            .isEqualTo(4)
            .jsonPath("$.result.roots.length()")
            .isEqualTo(3);
    }

    @Test
    void listEnvironments_returnsImportedGraphEnvironments() {
        importResourceOntologyAndSnapshot();

        webTestClient.get()
            .uri("/operation-intelligence/graph/environments?ontologyId=huawei-mo-resource-v1")
            .header("x-secret-key", SECRET_KEY)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.result.length()")
            .isEqualTo(1)
            .jsonPath("$.result[0].envCode")
            .isEqualTo("prod")
            .jsonPath("$.result[0].envName")
            .isEqualTo("prod");
    }

    @Test
    void listEnvironments_excludesDeletedGraphEnvironment() {
        importResourceOntologyAndSnapshot();

        webTestClient.post()
            .uri("/operation-intelligence/graph/admin/delete-entities")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("ontologyId", "huawei-mo-resource-v1", "envCode", "prod"))
            .exchange()
            .expectStatus()
            .isOk();

        webTestClient.get()
            .uri("/operation-intelligence/graph/environments?ontologyId=huawei-mo-resource-v1")
            .header("x-secret-key", SECRET_KEY)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.result.length()")
            .isEqualTo(0);
    }

    @Test
    void queryObservations_filtersByEntityAndSeverity() throws IOException {
        importFixture();

        webTestClient.post()
            .uri("/operation-intelligence/graph/observations/query")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("envCode", "prod", "entityId", "biz-prod-604015020", "severity", "warning"))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(true)
            .jsonPath("$.result.total")
            .isEqualTo(2)
            .jsonPath("$.result.results[0].name")
            .isEqualTo("business_flow_success_rate");
    }

    @Test
    void findImpactPath_returnsShortestPath() throws IOException {
        importFixture();

        webTestClient.post()
            .uri("/operation-intelligence/graph/impact-path")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("envCode", "prod", "fromEntityId", "svc-prod-bes-business-common-sysparambs",
                "toEntityId", "cluster-prod-rsp", "maxHops", 4))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.result.found")
            .isEqualTo(true)
            .jsonPath("$.result.hopCount")
            .isEqualTo(1)
            .jsonPath("$.result.path.relations[0].type")
            .isEqualTo("deployed_in");
    }

    @Test
    void findImpactPath_rejectsMissingEnvCode() throws IOException {
        importFixture();

        webTestClient.post()
            .uri("/operation-intelligence/graph/impact-path")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("fromEntityId", "svc-prod-bes-business-common-sysparambs",
                "toEntityId", "cluster-prod-rsp", "maxHops", 4))
            .exchange()
            .expectStatus()
            .isBadRequest()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(false)
            .jsonPath("$.error")
            .value(Matchers.containsString("envCode is required"));
    }

    @Test
    void findImpactPath_respectsRelationDirection() throws IOException {
        importFixture();

        webTestClient.post()
            .uri("/operation-intelligence/graph/impact-path")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("envCode", "prod", "fromEntityId", "cluster-prod-rsp",
                "toEntityId", "svc-prod-bes-business-common-sysparambs", "maxHops", 4))
            .exchange()
            .expectStatus()
            .isNotFound()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(false)
            .jsonPath("$.error")
            .value(Matchers.containsString("Impact path not found"));
    }

    @Test
    void rootCauseCandidates_returnsAbnormalObservationEvidence() throws IOException {
        importFixture();

        webTestClient.post()
            .uri("/operation-intelligence/graph/root-cause-candidates")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("envCode", "prod", "entityId", "biz-prod-604015020", "maxHops", 0))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.result.total")
            .isEqualTo(2)
            .jsonPath("$.result.results[0].entityId")
            .isEqualTo("biz-prod-604015020")
            .jsonPath("$.result.results[0].score")
            .isEqualTo(60);
    }

    @Test
    void diagnosisContext_returnsEntitySubgraphObservationsAndCandidates() throws IOException {
        importFixture();

        webTestClient.post()
            .uri("/operation-intelligence/graph/diagnosis/context")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("envCode", "prod", "entityId", "biz-prod-604015020", "maxHops", 0))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.result.entity.id")
            .isEqualTo("biz-prod-604015020")
            .jsonPath("$.result.subgraph.entities.length()")
            .isEqualTo(1)
            .jsonPath("$.result.observations.length()")
            .isEqualTo(2)
            .jsonPath("$.result.rootCauseCandidates.length()")
            .isEqualTo(2);
    }

    @Test
    void exportGraph_returnsNativeJsonPackage() throws IOException {
        importFixture();

        webTestClient.post()
            .uri("/operation-intelligence/graph/admin/export")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("envCode", "prod"))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(true)
            .jsonPath("$.result.manifest.format")
            .isEqualTo("KG_NATIVE_JSON")
            .jsonPath("$.result.manifest.entityCount")
            .isEqualTo(4)
            .jsonPath("$.result.manifest.relationCount")
            .isEqualTo(2)
            .jsonPath("$.result.manifest.observationCount")
            .isEqualTo(2)
            .jsonPath("$.result.schemaDsl")
            .value(Matchers.containsString("BusinessCapability"))
            .jsonPath("$.result.schemaDsl")
            .value(Matchers.containsString("deployed_in"))
            .jsonPath("$.result.snapshot.entities.length()")
            .isEqualTo(4)
            .jsonPath("$.result.snapshot.relations[0].type")
            .isEqualTo("deployed_in");
    }

    @Test
    void serviceLoadsPersistedSnapshotsOnStartupInit() throws IOException {
        importFixture();

        knowledgeGraphService.init();
        GraphSnapshot subgraph = knowledgeGraphService.querySubgraph("b2b-callchain-v1", "prod",
            "svc-prod-bes-business-common-datadictbs", 4);
        Assertions.assertEquals(3, subgraph.getEntities().size());
        Assertions.assertEquals(2, subgraph.getRelations().size());
    }

    @Test
    void dynamicOntology_supportsResourceDeploymentAndRuntimeRelations() {
        webTestClient.post()
            .uri("/operation-intelligence/graph/ontologies")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(resourceOntology())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.result.ontologyId")
            .isEqualTo("huawei-mo-resource-v1")
            .jsonPath("$.result.relationTypes.length()")
            .isEqualTo(3);

        webTestClient.post()
            .uri("/operation-intelligence/graph/admin/import")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(resourceSnapshot())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.result.entityCount")
            .isEqualTo(4)
            .jsonPath("$.result.relationCount")
            .isEqualTo(3);

        webTestClient.post()
            .uri("/operation-intelligence/graph/admin/export")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("ontologyId", "huawei-mo-resource-v1", "envCode", "prod"))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.result.manifest.ontologyId")
            .isEqualTo("huawei-mo-resource-v1")
            .jsonPath("$.result.schemaDsl")
            .value(Matchers.containsString("runs_on_host"))
            .jsonPath("$.result.snapshot.ontologyId")
            .isEqualTo("huawei-mo-resource-v1");
    }

    @Test
    void deleteGraphEntities_removesSelectedOntologyEnvironment() {
        importResourceOntologyAndSnapshot();

        webTestClient.post()
            .uri("/operation-intelligence/graph/admin/delete-entities")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("ontologyId", "huawei-mo-resource-v1", "envCode", "prod"))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.result.deleted")
            .isEqualTo(true)
            .jsonPath("$.result.envCode")
            .isEqualTo("prod");

        webTestClient.post()
            .uri("/operation-intelligence/graph/admin/export")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("ontologyId", "huawei-mo-resource-v1", "envCode", "prod"))
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    void deleteOntology_rejectsWhenEntitySnapshotsExist() {
        importResourceOntologyAndSnapshot();

        webTestClient.post()
            .uri("/operation-intelligence/graph/admin/delete-ontology")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("ontologyId", "huawei-mo-resource-v1"))
            .exchange()
            .expectStatus()
            .isBadRequest()
            .expectBody()
            .jsonPath("$.error")
            .isEqualTo("Ontology has entity snapshots. Delete entities before deleting ontology.");
    }

    @Test
    void deleteOntology_removesOntologyAfterEntitiesAreDeleted() {
        importResourceOntologyAndSnapshot();

        webTestClient.post()
            .uri("/operation-intelligence/graph/admin/delete-entities")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("ontologyId", "huawei-mo-resource-v1", "envCode", "prod"))
            .exchange()
            .expectStatus()
            .isOk();

        webTestClient.post()
            .uri("/operation-intelligence/graph/admin/delete-ontology")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("ontologyId", "huawei-mo-resource-v1"))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.result.deleted")
            .isEqualTo(true)
            .jsonPath("$.result.ontologyId")
            .isEqualTo("huawei-mo-resource-v1");

        webTestClient.get()
            .uri("/operation-intelligence/graph/ontologies/huawei-mo-resource-v1")
            .header("x-secret-key", SECRET_KEY)
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    private void importFixture() throws IOException {
        importB2bOntology();
        String body = new ClassPathResource("knowledgegraph/b2b-callchain-import.json")
            .getContentAsString(StandardCharsets.UTF_8);
        webTestClient.post()
            .uri("/operation-intelligence/graph/admin/import")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(true)
            .jsonPath("$.result.entityCount")
            .isEqualTo(4)
            .jsonPath("$.result.relationCount")
            .isEqualTo(2)
            .jsonPath("$.result.observationCount")
            .isEqualTo(2);
    }

    private void importB2bOntology() {
        webTestClient.post()
            .uri("/operation-intelligence/graph/ontologies")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("ontologyId", "b2b-callchain-v1", "name", "B2B Call Chain Ontology", "version", "1.0",
                "sourceSystem", "test", "entityTypes",
                List.of(entityType("BusinessCapability", List.of("menuId", "menuName")),
                    entityType("Service", List.of("serviceName")),
                    entityType("Cluster", List.of("clusterName"))),
                "relationTypes",
                List.of(
                    relationType("deployed_in", "deployment", List.of("Service"),
                        List.of("Cluster")),
                    relationType("calls", "runtime", List.of("Service"), List.of("Service")))))
            .exchange()
            .expectStatus()
            .isOk();
    }

    private Map<String, Object> resourceOntology() {
        return Map.of("ontologyId", "huawei-mo-resource-v1", "name", "Huawei MO Resource Ontology", "version", "1.0",
            "sourceSystem", "test", "entityTypes",
            List.of(entityType("ApplicationComponent", List.of("dn", "rawType")),
                entityType("ComputeNode", List.of("dn", "rawType")),
                entityType("Host", List.of("hostIp")),
                entityType("Container", List.of("dn", "rawType"))),
            "relationTypes",
            List.of(relationType("contains", "deployment", List.of("*"), List.of("*")),
                relationType("runs_on_host", "deployment", List.of("ApplicationComponent", "Container"),
                    List.of("Host")),
                relationType("calls", "runtime", List.of("ApplicationComponent"),
                    List.of("ApplicationComponent"))));
    }

    private Map<String, Object> resourceSnapshot() {
        return Map.of("ontologyId", "huawei-mo-resource-v1", "envCode", "prod", "schemaVersion", "1.0", "sourceSystem",
            "mo-export", "importMode", "UPSERT", "entities",
            List.of(
                entity("app-order", "ApplicationComponent", "OrderApp",
                    Map.of("dn", "app-order", "rawType", "OrderSchedulerApp", "serviceName", "OrderApp")),
                entity("app-pay", "ApplicationComponent", "PayApp",
                    Map.of("dn", "app-pay", "rawType", "PaymentApp", "serviceName", "PayApp")),
                entity("container-order", "Container", "order-docker",
                    Map.of("dn", "docker:order", "rawType", "Docker")),
                entity("host-192-168-200-42", "Host", "host-192-168-200-42", Map.of("hostIp", "192.168.200.42"))),
            "relations",
            List.of(relation("rel-app-container", "contains", "app-order", "container-order"),
                relation("rel-container-host", "runs_on_host", "container-order", "host-192-168-200-42"),
                relation("rel-app-calls", "calls", "app-order", "app-pay")));
    }

    private void importResourceOntologyAndSnapshot() {
        webTestClient.post()
            .uri("/operation-intelligence/graph/ontologies")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(resourceOntology())
            .exchange()
            .expectStatus()
            .isOk();
        webTestClient.post()
            .uri("/operation-intelligence/graph/admin/import")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(resourceSnapshot())
            .exchange()
            .expectStatus()
            .isOk();
    }

    private Map<String, Object> entityType(String type, List<String> requiredProperties) {
        return Map.of("type", type, "requiredProperties", requiredProperties);
    }

    private Map<String, Object> relationType(String type, String layer, List<String> from,
        List<String> to) {
        return Map.of("type", type, "layer", layer, "from", from, "to", to);
    }

    private Map<String, Object> entity(String id, String type, String name, Map<String, Object> properties) {
        return Map.of("id", id, "type", type, "name", name, "displayName", name, "properties", properties);
    }

    private Map<String, Object> relation(String id, String type, String from, String to) {
        return Map.of("id", id, "type", type, "from", from, "to", to);
    }
}
