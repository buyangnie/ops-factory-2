/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.service.BusinessTypeService;

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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for CRUD operations on business type definitions.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RestController
@RequestMapping("/gateway/business-types")
public class BusinessTypeController {
    private final BusinessTypeService businessTypeService;

    /**
     * Creates the business type controller instance.
     *
     * @author x00000000
     * @since 2026-05-09
     */
    public BusinessTypeController(BusinessTypeService businessTypeService) {
        this.businessTypeService = businessTypeService;
    }

    /**
     * Lists all business type definitions.
     *
     * @param exchange the exchange parameter
     * @return the result
     */
    @GetMapping
    public Mono<Map<String, Object>> listBusinessTypes(ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> types = businessTypeService.listBusinessTypes();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("businessTypes", types);
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Gets a business type by ID.
     *
     * @param id the id parameter
     * @param exchange the exchange parameter
     * @return the result
     */
    @GetMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> getBusinessType(@PathVariable("id") String id,
        ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> {
            try {
                Map<String, Object> bt = businessTypeService.getBusinessType(id);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", true);
                body.put("businessType", bt);
                return ResponseEntity.ok(body);
            } catch (IllegalArgumentException e) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("error", "Business type not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Creates a new business type.
     *
     * @param request the request parameter
     * @param exchange the exchange parameter
     * @return the result
     */
    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> createBusinessType(@RequestBody Map<String, Object> request,
        ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> {
            try {
                Map<String, Object> bt = businessTypeService.createBusinessType(request);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", true);
                body.put("businessType", bt);
                return ResponseEntity.status(HttpStatus.CREATED).body(body);
            } catch (IllegalArgumentException e) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("error", "Invalid business type request");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Updates a business type by ID.
     *
     * @param id the id parameter
     * @param request the request parameter
     * @param exchange the exchange parameter
     * @return the result
     */
    @PutMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> updateBusinessType(@PathVariable("id") String id,
        @RequestBody Map<String, Object> request, ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> {
            try {
                Map<String, Object> bt = businessTypeService.updateBusinessType(id, request);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", true);
                body.put("businessType", bt);
                return ResponseEntity.ok(body);
            } catch (IllegalArgumentException e) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("error", "Business type not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Deletes a business type by ID.
     *
     * @param id the id parameter
     * @param exchange the exchange parameter
     * @return the result
     */
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteBusinessType(@PathVariable("id") String id,
        ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> {
            boolean deleted = businessTypeService.deleteBusinessType(id);
            if (!deleted) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("error", "Business type not found: " + id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            return ResponseEntity.ok(body);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
