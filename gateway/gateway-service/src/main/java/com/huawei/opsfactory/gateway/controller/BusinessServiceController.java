/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.service.BusinessServiceService;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
import org.springframework.web.server.ServerWebExchange;

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
@RequestMapping("/gateway/business-services")
public class BusinessServiceController {
    private final BusinessServiceService businessServiceService;

    /**
     * Creates the business service controller instance.
     *
     * @author x00000000
     * @since 2026-05-09
     */
    public BusinessServiceController(BusinessServiceService businessServiceService) {
        this.businessServiceService = businessServiceService;
    }

    /**
     * Lists business services, optionally filtered by group, host, or keyword.
     *
     * @param groupId the groupId parameter
     * @param hostId the hostId parameter
     * @param keyword the keyword parameter
     * @param exchange the exchange parameter
     * @return the result
     */
    @GetMapping
    public Mono<Map<String, Object>> listBusinessServices(
        @RequestParam(value = "groupId", required = false) String groupId,
        @RequestParam(value = "hostId", required = false) String hostId,
        @RequestParam(value = "keyword", required = false) String keyword, ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> services;
            if (keyword != null && !keyword.isEmpty()) {
                services = businessServiceService.searchByKeyword(keyword);
            } else {
                services = businessServiceService.listBusinessServices(groupId, hostId);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("businessServices", services);
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Gets a business service by ID.
     *
     * @param id the id parameter
     * @param exchange the exchange parameter
     * @return the result
     */
    @GetMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> getBusinessService(@PathVariable("id") String id,
        ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> {
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
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Gets a business service with its associated hosts resolved.
     *
     * @param id the id parameter
     * @param exchange the exchange parameter
     * @return the result
     */
    @GetMapping("/{id}/resolved")
    public Mono<ResponseEntity<Map<String, Object>>> getResolved(@PathVariable("id") String id,
        ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> {
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
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Lists hosts associated with a business service.
     *
     * @param id the id parameter
     * @param exchange the exchange parameter
     * @return the result
     */
    @GetMapping("/{id}/hosts")
    public Mono<Map<String, Object>> getHosts(@PathVariable("id") String id, ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> hosts = businessServiceService.getHostsForBusinessService(id);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("hosts", hosts);
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Gets the topology data for a business service.
     *
     * @param id the id parameter
     * @param exchange the exchange parameter
     * @return the result
     */
    @GetMapping("/{id}/topology")
    public Mono<Map<String, Object>> getTopology(@PathVariable("id") String id, ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> businessServiceService.getTopologyForBusinessService(id))
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Creates a new business service.
     *
     * @param request the request parameter
     * @param exchange the exchange parameter
     * @return the result
     */
    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> createBusinessService(@RequestBody Map<String, Object> request,
        ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> {
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
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Updates a business service by ID.
     *
     * @param id the id parameter
     * @param request the request parameter
     * @param exchange the exchange parameter
     * @return the result
     */
    @PutMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> updateBusinessService(@PathVariable("id") String id,
        @RequestBody Map<String, Object> request, ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> {
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
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Deletes a business service by ID.
     *
     * @param id the id parameter
     * @param exchange the exchange parameter
     * @return the result
     */
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteBusinessService(@PathVariable("id") String id,
        ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> {
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
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Migrates business data from the legacy business field to the business service table.
     *
     * @param exchange the exchange parameter
     * @return the result
     */
    @PostMapping("/migrate")
    public Mono<Map<String, Object>> migrate(ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> businessServiceService.migrateFromBusinessField())
            .subscribeOn(Schedulers.boundedElastic());
    }
}
