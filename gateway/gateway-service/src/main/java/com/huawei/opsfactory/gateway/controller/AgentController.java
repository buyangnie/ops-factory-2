/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.common.model.AgentRegistryEntry;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.process.InstanceManager;
import com.huawei.opsfactory.gateway.service.AgentConfigService;

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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for managing agent registration, configuration, skills, and memory.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RestController
@RequestMapping("/gateway/agents")
public class AgentController {
    private final AgentConfigService agentConfigService;

    private final InstanceManager instanceManager;

    /**
     * Creates the agent controller instance.
     *
     * @author x00000000
     * @since 2026-05-09
     */
    public AgentController(AgentConfigService agentConfigService, InstanceManager instanceManager) {
        this.agentConfigService = agentConfigService;
        this.instanceManager = instanceManager;
    }

    /**
     * Lists all registered agents with their status, provider, model, and skills.
     *
     * @return the result
     */
    @GetMapping
    public Mono<Map<String, Object>> listAgents() {
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> agents = agentConfigService.getRegistry().stream().map(entry -> {
                Map<String, Object> config = Map.of();
                String status = "configured";
                String error = null;
                try {
                    config = agentConfigService.loadAgentConfigYaml(entry.id());
                } catch (IllegalStateException e) {
                    status = "invalid_config";
                    error = e.getMessage();
                }

                List<Map<String, String>> skills;
                try {
                    skills = agentConfigService.listSkills(entry.id());
                } catch (IllegalStateException e) {
                    skills = List.of();
                    if (error == null) {
                        status = "invalid_config";
                        error = e.getMessage();
                    }
                }
                Map<String, Object> agentMap = new LinkedHashMap<>();
                agentMap.put("id", entry.id());
                agentMap.put("name", entry.name());
                agentMap.put("status", status);
                agentMap.put("provider", config.getOrDefault("GOOSE_PROVIDER", ""));
                agentMap.put("model", config.getOrDefault("GOOSE_MODEL", ""));
                agentMap.put("skills", skills);
                if (error != null && !error.isBlank()) {
                    agentMap.put("error", error);
                }
                return (Map<String, Object>) agentMap;
            }).toList();
            return Map.<String, Object> of("agents", agents);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Creates a new agent with the given ID and name.
     *
     * @param body the body parameter
     * @param exchange the exchange parameter
     * @return the result
     */
    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> createAgent(@RequestBody Map<String, String> body,
        ServerWebExchange exchange) {
        requireAdmin(exchange);
        String id = body.get("id");
        String name = body.get("name");
        if (id == null || id.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agent ID is required");
        }
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agent name is required");
        }
        try {
            Map<String, Object> agent = agentConfigService.createAgent(id.strip(), name.strip());
            return Mono
                .just(ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", (Object) true, "agent", agent)));
        } catch (IllegalArgumentException e) {
            Map<String, Object> errorBody = new LinkedHashMap<>();
            errorBody.put("success", false);
            errorBody.put("error", e.getMessage());
            return Mono.just(ResponseEntity.badRequest().body(errorBody));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create agent", e);
        }
    }

    /**
     * Deletes an agent by ID and stops all its running instances.
     *
     * @param id the id parameter
     * @param exchange the exchange parameter
     * @return the result
     */
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteAgent(@PathVariable("id") String id, ServerWebExchange exchange) {
        requireAdmin(exchange);
        try {
            instanceManager.stopAllForAgent(id);
            agentConfigService.deleteAgent(id);
            return Mono.just(ResponseEntity.ok(Map.of("success", (Object) true)));
        } catch (IllegalArgumentException e) {
            Map<String, Object> errorBody = new LinkedHashMap<>();
            errorBody.put("success", false);
            errorBody.put("error", e.getMessage());
            return Mono.just(ResponseEntity.badRequest().body(errorBody));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete agent", e);
        }
    }

    /**
     * Lists all skills configured for the specified agent.
     *
     * @param id the id parameter
     * @param exchange the exchange parameter
     * @return the result
     */
    @GetMapping("/{id}/skills")
    public Mono<Map<String, Object>> listSkills(@PathVariable("id") String id, ServerWebExchange exchange) {
        requireAdmin(exchange);
        return Mono.just(Map.of("skills", agentConfigService.listSkills(id)));
    }

    /**
     * Gets the full configuration for the specified agent.
     *
     * @param id the id parameter
     * @param exchange the exchange parameter
     * @return the result
     */
    @GetMapping("/{id}/config")
    public Mono<ResponseEntity<Map<String, Object>>> getConfig(@PathVariable("id") String id, ServerWebExchange exchange) {
        requireAdmin(exchange);
        AgentRegistryEntry entry = agentConfigService.findAgent(id);
        if (entry == null) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        Map<String, Object> config = agentConfigService.loadAgentConfigYaml(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", entry.id());
        result.put("name", entry.name());
        result.put("agentsMd", agentConfigService.readAgentsMd(id));
        result.put("provider", config.getOrDefault("GOOSE_PROVIDER", ""));
        result.put("model", config.getOrDefault("GOOSE_MODEL", ""));
        result.put("workingDir", agentConfigService.getAgentsDir().resolve(id).toString());
        return Mono.just(ResponseEntity.ok(result));
    }

    /**
     * Updates the agents.md configuration for the specified agent.
     *
     * @param id the id parameter
     * @param body the body parameter
     * @param exchange the exchange parameter
     * @return the result
     */
    @PutMapping("/{id}/config")
    public Mono<ResponseEntity<Map<String, Object>>> updateConfig(@PathVariable("id") String id,
        @RequestBody Map<String, String> body, ServerWebExchange exchange) {
        requireAdmin(exchange);
        AgentRegistryEntry entry = agentConfigService.findAgent(id);
        if (entry == null) {
            Map<String, Object> errorBody = new LinkedHashMap<>();
            errorBody.put("success", false);
            errorBody.put("error", "Agent '" + id + "' not found");
            return Mono.just(ResponseEntity.badRequest().body(errorBody));
        }
        String agentsMd = body.get("agentsMd");
        if (agentsMd != null) {
            try {
                agentConfigService.writeAgentsMd(id, agentsMd);
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update config", e);
            }
        }
        return Mono.just(ResponseEntity.ok(Map.of("success", (Object) true)));
    }

    // ── Memory endpoints ──────────────────────────────────────────

    private static final java.util.regex.Pattern CATEGORY_PATTERN = java.util.regex.Pattern.compile("^[a-zA-Z0-9_-]+$");

    /**
     * Lists all memory files for the specified agent.
     *
     * @param id the id parameter
     * @param exchange the exchange parameter
     * @return the result
     */
    @GetMapping("/{id}/memory")
    public Mono<ResponseEntity<Map<String, Object>>> listMemory(@PathVariable("id") String id, ServerWebExchange exchange) {
        requireAdmin(exchange);
        return Mono.fromCallable(() -> {
            List<Map<String, String>> files = agentConfigService.listMemoryFiles(id);
            return ResponseEntity.ok(Map.<String, Object> of("files", files));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Gets the content of a specific memory category for the specified agent.
     *
     * @param id the id parameter
     * @param category the category parameter
     * @param exchange the exchange parameter
     * @return the result
     */
    @GetMapping("/{id}/memory/{category}")
    public Mono<ResponseEntity<Map<String, Object>>> getMemoryFile(@PathVariable("id") String id,
        @PathVariable("category") String category, ServerWebExchange exchange) {
        requireAdmin(exchange);
        if (!isValidCategory(category)) {
            return badCategory();
        }
        return Mono.<ResponseEntity<Map<String, Object>>> fromCallable(() -> {
            String content = agentConfigService.readMemoryFile(id, category);
            if (content == null) {
                return ResponseEntity.notFound().<Map<String, Object>> build();
            }
            return ResponseEntity.ok(Map.<String, Object> of("category", category, "content", content));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Writes content to a specific memory category for the specified agent.
     *
     * @param id the id parameter
     * @param category the category parameter
     * @param body the body parameter
     * @param exchange the exchange parameter
     * @return the result
     */
    @PutMapping("/{id}/memory/{category}")
    public Mono<ResponseEntity<Map<String, Object>>> putMemoryFile(@PathVariable("id") String id,
        @PathVariable("category") String category, @RequestBody Map<String, String> body, ServerWebExchange exchange) {
        requireAdmin(exchange);
        if (!isValidCategory(category)) {
            return badCategory();
        }
        return Mono.fromCallable(() -> {
            try {
                agentConfigService.writeMemoryFile(id, category, body.getOrDefault("content", ""));
                return ResponseEntity.ok(Map.<String, Object> of("success", (Object) true));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                    .body(Map.<String, Object> of("success", (Object) false, "error", e.getMessage()));
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Deletes a specific memory category for the specified agent.
     *
     * @param id the id parameter
     * @param category the category parameter
     * @param exchange the exchange parameter
     * @return the result
     */
    @DeleteMapping("/{id}/memory/{category}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteMemoryFile(@PathVariable("id") String id,
        @PathVariable("category") String category, ServerWebExchange exchange) {
        requireAdmin(exchange);
        if (!isValidCategory(category)) {
            return badCategory();
        }
        return Mono.fromCallable(() -> {
            try {
                agentConfigService.deleteMemoryFile(id, category);
                return ResponseEntity.ok(Map.<String, Object> of("success", (Object) true));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                    .body(Map.<String, Object> of("success", (Object) false, "error", e.getMessage()));
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private static boolean isValidCategory(String category) {
        return CATEGORY_PATTERN.matcher(category).matches();
    }

    private static Mono<ResponseEntity<Map<String, Object>>> badCategory() {
        return Mono.just(
            ResponseEntity.badRequest().body(Map.of("success", (Object) false, "error", "Invalid category name")));
    }

    private void requireAdmin(ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
    }
}
