/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.service.CommandWhitelistService;

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
import java.util.Map;

/**
 * REST controller for managing the remote execution command whitelist.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RestController
@RequestMapping("/gateway/command-whitelist")
public class CommandWhitelistController {
    private final CommandWhitelistService commandWhitelistService;

    /**
     * Creates the command whitelist controller instance.
     *
     * @author x00000000
     * @since 2026-05-09
     */
    public CommandWhitelistController(CommandWhitelistService commandWhitelistService) {
        this.commandWhitelistService = commandWhitelistService;
    }

    /**
     * Returns the current command whitelist configuration.
     *
     * @param exchange the exchange parameter
     * @return the result
     */
    @GetMapping({"", "/"})
    public Mono<Map<String, Object>> getWhitelist(ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> {
            Map<String, Object> whitelist = commandWhitelistService.getWhitelist();
            return whitelist;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Adds a command pattern to the whitelist.
     *
     * @param request the request parameter
     * @param exchange the exchange parameter
     * @return the result
     */
    @PostMapping({"", "/"})
    public Mono<ResponseEntity<Map<String, Object>>> addCommand(@RequestBody Map<String, Object> request,
        ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> {
            try {
                commandWhitelistService.addCommand(request);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", true);
                body.put("command", request);
                return ResponseEntity.status(HttpStatus.CREATED).body(body);
            } catch (IllegalArgumentException e) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("error", "Command whitelist entry conflict");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Updates a command pattern in the whitelist.
     *
     * @param pattern the pattern parameter
     * @param request the request parameter
     * @param exchange the exchange parameter
     * @return the result
     */
    @PutMapping("/{pattern}")
    public Mono<ResponseEntity<Map<String, Object>>> updateCommand(@PathVariable("pattern") String pattern,
        @RequestBody Map<String, Object> request, ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> {
            try {
                commandWhitelistService.updateCommand(pattern, request);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", true);
                body.put("command", request);
                return ResponseEntity.ok(body);
            } catch (IllegalArgumentException e) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("error", "Command not found: " + pattern);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Deletes a command pattern from the whitelist.
     *
     * @param pattern the pattern parameter
     * @param exchange the exchange parameter
     * @return the result
     */
    @DeleteMapping("/{pattern}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteCommand(@PathVariable("pattern") String pattern,
        ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> {
            try {
                commandWhitelistService.deleteCommand(pattern);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", true);
                return ResponseEntity.ok(body);
            } catch (IllegalArgumentException e) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("error", "Command not found: " + pattern);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
