/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.service.ClusterRelationService;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.servicecomb.provider.rest.common.RestSchema;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for managing cluster-to-cluster relation edges and graph queries.
 *
 * @author x00000000
 * @since 2026-05-09
 */

@RestController
@RestSchema(schemaId = "clusterRelationController")
@RequestMapping("/gateway/cluster-relations")
public class ClusterRelationController {
    private final ClusterRelationService clusterRelationService;

    /**
     * Creates the cluster relation controller instance.
     *
     * @param clusterRelationService service handling cluster relation CRUD operations
     */
    public ClusterRelationController(ClusterRelationService clusterRelationService) {
        this.clusterRelationService = clusterRelationService;
    }

    /**
     * Lists cluster relations, optionally filtered by cluster ID.
     *
     * @param clusterId optional cluster identifier to filter relations
     * @param exchange current HTTP exchange carrying user context
     * @return Mono emitting a map with "relations" list
     */
    @GetMapping
    public Map<String, Object> listRelations(@RequestParam(value = "clusterId", required = false) String clusterId,
        HttpServletRequest request) {
        List<Map<String, Object>> relations = clusterRelationService.listRelations(clusterId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("relations", relations);
        return result;
    }

    /**
     * Returns the cluster relation graph data for visualization.
     *
     * @param groupId optional group identifier to filter graph data
     * @param exchange current HTTP exchange carrying user context
     * @return Mono emitting a map with graph nodes and edges
     */
    @GetMapping("/graph")
    public Map<String, Object> getGraph(@RequestParam(value = "groupId", required = false) String groupId,
        HttpServletRequest request) {
        return clusterRelationService.getGraphData(groupId);
    }

    /**
     * Returns the neighbor clusters for a given cluster.
     *
     * @param clusterId cluster identifier to look up neighbors for
     * @param exchange current HTTP exchange carrying user context
     * @return Mono emitting a map with neighbor cluster data
     */
    @GetMapping("/clusters/{clusterId}/neighbors")
    public Map<String, Object> getClusterNeighbors(@PathVariable("clusterId") String clusterId,
        HttpServletRequest request) {
        return clusterRelationService.getClusterNeighbors(clusterId);
    }

    /**
     * Returns the neighbor hosts for a given host via cluster relations.
     *
     * @param hostId host identifier to look up neighbors for
     * @param exchange current HTTP exchange carrying user context
     * @return Mono emitting a map with neighbor host data
     */
    @GetMapping("/hosts/{hostId}/neighbors")
    public Map<String, Object> getHostNeighbors(@PathVariable("hostId") String hostId, HttpServletRequest request) {
        return clusterRelationService.getHostNeighborsByCluster(hostId);
    }

    /**
     * Creates a new cluster relation edge.
     *
     * @param request request body containing relation fields
     * @param exchange current HTTP exchange carrying user context
     * @return Mono emitting ResponseEntity with created relation or 400
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createRelation(@RequestBody Map<String, Object> requestBody,
        HttpServletRequest request) {
        try {
            Map<String, Object> relation = clusterRelationService.createRelation(requestBody);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("relation", relation);
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } catch (IllegalArgumentException e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("error", "Invalid cluster relation request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        }
    }

    /**
     * Updates a cluster relation by ID.
     *
     * @param id relation identifier
     * @param request request body containing updated fields
     * @param exchange current HTTP exchange carrying user context
     * @return Mono emitting ResponseEntity with updated relation or 404
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateRelation(@PathVariable("id") String id,
        @RequestBody Map<String, Object> requestBody, HttpServletRequest request) {
        try {
            Map<String, Object> relation = clusterRelationService.updateRelation(id, requestBody);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("relation", relation);
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("error", "Cluster relation not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        }
    }

    /**
     * Deletes a cluster relation by ID.
     *
     * @param id relation identifier
     * @param exchange current HTTP exchange carrying user context
     * @return Mono emitting ResponseEntity with success status or 404
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteRelation(@PathVariable("id") String id,
        HttpServletRequest request) {
        boolean deleted = clusterRelationService.deleteRelation(id);
        if (!deleted) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("error", "Cluster relation not found: " + id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        return ResponseEntity.ok(body);
    }
}
