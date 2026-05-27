/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.service.BusinessServiceService;

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
 * REST controller for CRUD operations on business service definitions.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RestController
@RestSchema(schemaId = "businessServiceController")
@RequestMapping("/gateway/business-services")
public class BusinessServiceController {
    private final BusinessServiceService businessServiceService;

    /**
     * Creates the business service controller instance.
     *
     * @param businessServiceService service handling business service CRUD operations
     */
    public BusinessServiceController(BusinessServiceService businessServiceService) {
        this.businessServiceService = businessServiceService;
    }

    /**
     * Lists business services, optionally filtered by group, host, or keyword.
     *
     * @param groupId optional group identifier filter
     * @param hostId optional host identifier filter
     * @param keyword optional keyword for full-text search
     * @param request current HTTP request
     * @return a map with "businessServices" list
     */
    @GetMapping
    public Map<String, Object> listBusinessServices(@RequestParam(value = "groupId", required = false) String groupId,
        @RequestParam(value = "hostId", required = false) String hostId,
        @RequestParam(value = "keyword", required = false) String keyword, HttpServletRequest request) {
        List<Map<String, Object>> services;
        if (keyword != null && !keyword.isEmpty()) {
            services = businessServiceService.searchByKeyword(keyword);
        } else {
            services = businessServiceService.listBusinessServices(groupId, hostId);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("businessServices", services);
        return result;
    }

    /**
     * Gets a business service by ID.
     *
     * @param id business service identifier
     * @param request current HTTP request
     * @return ResponseEntity containing the business service or 404
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getBusinessService(@PathVariable("id") String id,
        HttpServletRequest request) {
        try {
            Map<String, Object> bs = businessServiceService.getBusinessService(id);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("businessService", bs);
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("error", "Business service not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        }
    }

    /**
     * Gets a business service with its associated hosts resolved.
     *
     * @param id business service identifier
     * @param request current HTTP request
     * @return ResponseEntity containing the resolved business service or 404
     */
    @GetMapping("/{id}/resolved")
    public ResponseEntity<Map<String, Object>> getResolved(@PathVariable("id") String id, HttpServletRequest request) {
        try {
            Map<String, Object> resolved = businessServiceService.getWithResolvedHosts(id);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("businessService", resolved);
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("error", "Business service not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        }
    }

    /**
     * Lists hosts associated with a business service.
     *
     * @param id business service identifier
     * @param request current HTTP request
     * @return a map with "hosts" list
     */
    @GetMapping("/{id}/hosts")
    public Map<String, Object> getHosts(@PathVariable("id") String id, HttpServletRequest request) {
        List<Map<String, Object>> hosts = businessServiceService.getHostsForBusinessService(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hosts", hosts);
        return result;
    }

    /**
     * Gets the topology data for a business service.
     *
     * @param id business service identifier
     * @param request current HTTP request
     * @return a map with topology data
     */
    @GetMapping("/{id}/topology")
    public Map<String, Object> getTopology(@PathVariable("id") String id, HttpServletRequest request) {
        return businessServiceService.getTopologyForBusinessService(id);
    }

    /**
     * Creates a new business service.
     *
     * @param request request body containing business service fields
     * @param httpRequest current HTTP request
     * @return ResponseEntity with created business service or 400
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createBusinessService(@RequestBody Map<String, Object> request,
        HttpServletRequest httpRequest) {
        try {
            Map<String, Object> bs = businessServiceService.createBusinessService(request);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("businessService", bs);
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } catch (IllegalArgumentException e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("error", "Invalid business service request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        }
    }

    /**
     * Updates a business service by ID.
     *
     * @param id business service identifier
     * @param request request body containing updated fields
     * @param httpRequest current HTTP request
     * @return ResponseEntity with updated business service or 404
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateBusinessService(@PathVariable("id") String id,
        @RequestBody Map<String, Object> request, HttpServletRequest httpRequest) {
        try {
            Map<String, Object> bs = businessServiceService.updateBusinessService(id, request);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("businessService", bs);
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("error", "Business service not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        }
    }

    /**
     * Deletes a business service by ID.
     *
     * @param id business service identifier
     * @param request current HTTP request
     * @return ResponseEntity with success status or 404
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteBusinessService(@PathVariable("id") String id,
        HttpServletRequest request) {
        boolean deleted = businessServiceService.deleteBusinessService(id);
        if (!deleted) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("error", "Business service not found: " + id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        return ResponseEntity.ok(body);
    }

    /**
     * Migrates business data from the legacy business field to the business service table.
     *
     * @param request current HTTP request
     * @return a map with migration result counts
     */
    @PostMapping("/migrate")
    public Map<String, Object> migrate(HttpServletRequest request) {
        return businessServiceService.migrateFromBusinessField();
    }
}