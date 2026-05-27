/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.service.BusinessServiceService;
import com.huawei.opsfactory.gateway.service.ClusterService;
import com.huawei.opsfactory.gateway.service.HostGroupService;
import com.huawei.opsfactory.gateway.service.HostService;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.servicecomb.provider.rest.common.RestSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST controller for CRUD operations and connectivity testing on host entries.
 *
 * @author x00000000
 * @since 2026-05-09
 */

@RestController
@RestSchema(schemaId = "hostController")
@RequestMapping("/gateway/hosts")
public class HostController {
    private static final Logger log = LoggerFactory.getLogger(HostController.class);

    private final HostService hostService;

    private final ClusterService clusterService;

    private final BusinessServiceService businessServiceService;

    private final HostGroupService hostGroupService;

    /**
     * Creates the host controller instance.
     */
    public HostController(HostService hostService, ClusterService clusterService,
        BusinessServiceService businessServiceService, HostGroupService hostGroupService) {
        this.hostService = hostService;
        this.clusterService = clusterService;
        this.businessServiceService = businessServiceService;
        this.hostGroupService = hostGroupService;
    }

    /**
     * Lists hosts, optionally filtered by tags, cluster, group, business service, or enabled status.
     *
     * @param tags tags
     * @param clusterId cluster identifier
     * @param groupId group identifier
     * @param businessServiceId business service id
     *        status
     * @param enabledOnly enabled-only filter flag
     * @param exchange server web exchange
     * @return the result
     */
    @GetMapping
    public Map<String, Object> listHosts(@RequestParam(value = "tags", required = false) String tags,
        @RequestParam(value = "clusterId", required = false) String clusterId,
        @RequestParam(value = "groupId", required = false) String groupId,
        @RequestParam(value = "businessServiceId", required = false) String businessServiceId,
        @RequestParam(value = "enabledOnly", required = false, defaultValue = "false") boolean enabledOnly,
        HttpServletRequest request) {

        // Resolve disabled context once when enabledOnly is requested
        DisabledSets disabledSets = buildDisabledSets(enabledOnly, groupId, clusterId);
        if (disabledSets == null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("hosts", List.of());
            return result;
        }

        List<Map<String, Object>> hosts = resolveHosts(businessServiceId, clusterId, groupId, tags);

        // Filter out hosts belonging to disabled clusters when enabledOnly=true
        if (enabledOnly && !disabledSets.clusterIds.isEmpty()) {
            hosts.removeIf(h -> disabledSets.clusterIds.contains(h.get("clusterId")));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hosts", hosts);
        return result;
    }

    /**
     * Builds the disabled group and cluster ID sets when enabledOnly is true.
     * Returns {@code null} to signal that the caller should return an empty-hosts
     * result immediately (the requested group or cluster is disabled).
     *
     * @param enabledOnly whether only enabled entities should be included
     * @param groupId optional group identifier filter
     * @param clusterId optional cluster identifier filter
     * @return disabled sets, or {@code null} if an early empty result is required
     */
    private DisabledSets buildDisabledSets(boolean enabledOnly, String groupId, String clusterId) {
        if (!enabledOnly) {
            return new DisabledSets(Set.of(), Set.of());
        }

        List<Map<String, Object>> allGroups = hostGroupService.listGroups();
        Set<String> disabledGroupIds = hostGroupService.getDisabledGroupIds(allGroups);

        if (groupId != null && !groupId.isEmpty() && disabledGroupIds.contains(groupId)) {
            return null;
        }
        if (clusterId != null && !clusterId.isEmpty()) {
            try {
                Map<String, Object> cluster = clusterService.getCluster(clusterId);
                if (Boolean.FALSE.equals(cluster.get("enabled")) || disabledGroupIds.contains(cluster.get("groupId"))) {
                    return null;
                }
            } catch (IllegalArgumentException e) {
                // Cluster not found, let normal flow handle it
                log.debug("Cluster not found for id: {}", clusterId);
                return null;
            }
        }

        // Build disabledClusterIds for the "list all hosts" fallback path
        List<Map<String, Object>> allClusters = clusterService.listClusters(null, null);
        Set<String> disabledClusterIds = new HashSet<>();
        for (Map<String, Object> c : allClusters) {
            if (Boolean.FALSE.equals(c.get("enabled")) || disabledGroupIds.contains(c.get("groupId"))) {
                disabledClusterIds.add((String) c.get("id"));
            }
        }
        return new DisabledSets(disabledGroupIds, disabledClusterIds);
    }

    /**
     * Resolves the host list based on the provided filter parameters.
     * Priority: businessServiceId &gt; clusterId &gt; groupId &gt; tags.
     *
     * @param businessServiceId optional business service identifier
     * @param clusterId optional cluster identifier
     * @param groupId optional group identifier
     * @param tags optional comma-separated tags
     * @return the resolved host list
     */
    private List<Map<String, Object>> resolveHosts(String businessServiceId, String clusterId, String groupId,
        String tags) {
        if (businessServiceId != null && !businessServiceId.isEmpty()) {
            return businessServiceService.getHostsForBusinessService(businessServiceId);
        }
        if (clusterId != null && !clusterId.isEmpty()) {
            return hostService.listHostsByCluster(clusterId);
        }
        if (groupId != null && !groupId.isEmpty()) {
            return hostService.listHostsByGroup(groupId, clusterService);
        }
        List<String> tagList =
            (tags != null && !tags.isBlank()) ? Arrays.asList(tags.split(",")) : Collections.emptyList();
        return hostService.listHosts(tagList.toArray(new String[0]));
    }

    /**
     * Simple holder for the disabled group and cluster ID sets used during host listing.
     */
    private static final class DisabledSets {
        final Set<String> groupIds;

        final Set<String> clusterIds;

        DisabledSets(Set<String> groupIds, Set<String> clusterIds) {
            this.groupIds = Set.copyOf(groupIds);
            this.clusterIds = Set.copyOf(clusterIds);
        }
    }

    /**
     * Gets a host by its IP address.
     *
     * @param ip ip
     * @param exchange server web exchange
     * @return a host by its IP address
     */
    @GetMapping("/by-ip")
    public ResponseEntity<Map<String, Object>> getHostByIp(@RequestParam("ip") String ip, HttpServletRequest request) {
        Map<String, Object> host = hostService.findByIp(ip);
        if (host == null) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("error", "Host not found for IP: " + ip);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("host", host);
        return ResponseEntity.ok(body);
    }

    /**
     * Gets a host by ID.
     *
     * @param id entity identifier
     * @param exchange server web exchange
     * @return a host by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getHost(@PathVariable("id") String id, HttpServletRequest request) {
        Map<String, Object> host;
        try {
            host = hostService.getHost(id);
        } catch (IllegalArgumentException e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("error", "Host not found: " + id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        }
        if (host == null) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("error", "Host not found: " + id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("host", host);
        return ResponseEntity.ok(body);
    }

    /**
     * Creates a new host.
     *
     * @param request HTTP request
     * @param exchange server web exchange
     * @return the result
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createHost(@RequestBody Map<String, Object> requestBody,
        HttpServletRequest request) {
        try {
            Map<String, Object> host = hostService.createHost(requestBody);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("host", host);
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } catch (IllegalArgumentException e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("error", "Invalid host request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        }
    }

    /**
     * Updates a host by ID.
     *
     * @param id a host by ID
     * @param request a host by ID
     * @param exchange a host by ID
     * @return the result
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateHost(@PathVariable("id") String id,
        @RequestBody Map<String, Object> requestBody, HttpServletRequest request) {
        try {
            Map<String, Object> host = hostService.updateHost(id, requestBody);
            if (host == null) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("error", "Host not found: " + id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("host", host);
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            HttpStatus status = e.getMessage() != null && e.getMessage().startsWith("Host not found:")
                ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
            body.put("error", status == HttpStatus.NOT_FOUND ? "Host not found" : "Invalid host request");
            return ResponseEntity.status(status).body(body);
        }
    }

    /**
     * Deletes a host by ID.
     *
     * @param id entity identifier
     * @param exchange server web exchange
     * @return the result
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteHost(@PathVariable("id") String id, HttpServletRequest request) {
        boolean deleted = hostService.deleteHost(id);
        if (!deleted) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("error", "Host not found: " + id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        return ResponseEntity.ok(body);
    }

    /**
     * Returns all unique host tags.
     *
     * @param exchange returns all unique host tags
     * @return all unique host tags
     */
    @GetMapping("/tags")
    public Map<String, Object> getTags(HttpServletRequest request) {
        List<String> tags = hostService.getAllTags();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tags", tags);
        return result;
    }

    /**
     * Tests SSH connectivity to a host.
     *
     * @param id tests SSH connectivity to a host
     * @param exchange tests SSH connectivity to a host
     * @return the tests SSH connectivity to a host
     */
    @PostMapping("/{id}/test")
    public Map<String, Object> testConnectivity(@PathVariable("id") String id, HttpServletRequest request) {
        long startedAt = System.currentTimeMillis();
        String userId = (String) request.getAttribute(UserContextFilter.USER_ID_ATTR);
        log.info("Host connectivity test started hostId={} userId={}", id, userId);
        Map<String, Object> testResult = hostService.testConnection(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", testResult.get("success"));
        result.put("hostId", id);
        result.put("reachable", testResult.get("reachable"));
        result.put("latencyMs", testResult.get("latencyMs"));
        if (testResult.containsKey("error")) {
            result.put("error", testResult.get("error"));
        }
        Object success = result.get("success");
        Object reachable = result.get("reachable");
        Object latencyMs = result.get("latencyMs");
        log.info(
            "Host connectivity test completed hostId={} userId={} success={} reachable={} latencyMs={} "
                + "durationMs={} testResultKeys={}",
            id, userId, success, reachable, latencyMs, System.currentTimeMillis() - startedAt, testResult.keySet());
        if (Boolean.FALSE.equals(success) && (reachable == null || latencyMs == null)) {
            log.warn("Host connectivity test returned missing fields hostId={} userId={} reachable={} latencyMs={} "
                + "testResultKeys={}", id, userId, reachable, latencyMs, testResult.keySet());
            if (testResult.containsKey("message")) {
                log.warn("Host connectivity test failure message hostId={} userId={} message={}", id, userId,
                    String.valueOf(testResult.get("message")));
            }
        }
        return result;
    }
}
