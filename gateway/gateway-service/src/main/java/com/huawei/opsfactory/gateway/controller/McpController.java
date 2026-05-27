/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.common.model.ManagedInstance;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.process.InstanceManager;
import com.huawei.opsfactory.gateway.proxy.GoosedProxy;
import com.huawei.opsfactory.gateway.service.AgentConfigService;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.servicecomb.provider.rest.common.RestSchema;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for managing MCP (Model Context Protocol) extensions on agent instances.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RestController
@RestSchema(schemaId = "mcpController")
@RequestMapping("/gateway/agents/{agentId}/mcp")
public class McpController {
    private static final String KNOWLEDGE_SERVICE_MCP = "knowledge-service";

    private static final String KNOWLEDGE_CLI_MCP = "knowledge-cli";

    private final InstanceManager instanceManager;

    private final GoosedProxy goosedProxy;

    private final AgentConfigService agentConfigService;

    /**
     * Creates the mcp controller instance.
     */
    public McpController(InstanceManager instanceManager, GoosedProxy goosedProxy,
        AgentConfigService agentConfigService) {
        this.instanceManager = instanceManager;
        this.goosedProxy = goosedProxy;
        this.agentConfigService = agentConfigService;
    }

    /**
     * Lists MCP extensions configured on the agent's system instance.
     *
     * @param agentId agent identifier
     * @param request current HTTP request
     * @return the result
     */
    @GetMapping
    public ResponseEntity<String> getMcpExtensions(@PathVariable("agentId") String agentId,
        HttpServletRequest request) {
        String userId = (String) request.getAttribute(UserContextFilter.USER_ID_ATTR);
        ManagedInstance instance = instanceManager.getOrSpawn(agentId, userId).block();
        String result = goosedProxy
            .fetchJson(instance.getPort(), HttpMethod.GET, "/config/extensions", null, 30, instance.getSecretKey())
            .block();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    /**
     * Creates a new MCP extension on the agent's system instance and recycles running instances.
     *
     * @param agentId agent identifier
     * @param body request body
     * @param request current HTTP request
     * @return the created MCP extension
     */
    @PostMapping
    public String createMcpExtension(@PathVariable("agentId") String agentId, @RequestBody String body,
        HttpServletRequest request) {
        String userId = (String) request.getAttribute(UserContextFilter.USER_ID_ATTR);
        ManagedInstance instance = instanceManager.getOrSpawn(agentId, userId).block();
        return goosedProxy
            .fetchJson(instance.getPort(), HttpMethod.POST, "/config/extensions", body, 30, instance.getSecretKey())
            .block();
    }

    /**
     * Deletes an MCP extension by name and recycles running instances.
     *
     * @param agentId agent identifier
     * @param name name value
     * @param request current HTTP request
     * @return the deletion result
     */
    @DeleteMapping("/{name}")
    public String deleteMcpExtension(@PathVariable("agentId") String agentId, @PathVariable("name") String name,
        HttpServletRequest request) {
        String userId = (String) request.getAttribute(UserContextFilter.USER_ID_ATTR);
        ManagedInstance instance = instanceManager.getOrSpawn(agentId, userId).block();
        return goosedProxy
            .fetchJson(instance.getPort(), HttpMethod.DELETE, "/config/extensions/" + name, null, 30,
                instance.getSecretKey())
            .block();
    }

    /**
     * Gets the settings for a specific MCP extension.
     *
     * @param agentId agent identifier
     * @param name name value
     * @param request current HTTP request
     * @return the settings for a specific MCP extension
     */
    @GetMapping("/{name}/settings")
    public ResponseEntity<Map<String, Object>> getMcpSettings(@PathVariable("agentId") String agentId,
        @PathVariable("name") String name, HttpServletRequest request) {
        try {
            Map<String, Object> settings = agentConfigService.readMcpSettings(agentId, name);
            if (hasConfigBackedSettings(name)) {
                if (settings == null) {
                    return ResponseEntity.ok(emptyKnowledgeSettings(name));
                }
                if (!settings.containsKey("sourceId")) {
                    settings.put("sourceId", null);
                }
                return ResponseEntity.ok(settings);
            }
            if (settings == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of());
            }
            return ResponseEntity.ok(settings);
        } catch (IllegalStateException e) {
            if (hasConfigBackedSettings(name)) {
                return ResponseEntity.ok(emptyKnowledgeSettings(name));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("code", "SETTINGS_READ_FAILED", "message", "Failed to read MCP settings"));
        }
    }

    /**
     * Updates the settings for a specific MCP extension.
     *
     * @param agentId agent identifier
     * @param name name value
     * @param body request body
     * @param request current HTTP request
     * @return the update result
     */
    @PutMapping("/{name}/settings")
    public ResponseEntity<Map<String, Object>> putMcpSettings(@PathVariable("agentId") String agentId,
        @PathVariable("name") String name, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            agentConfigService.writeMcpSettings(agentId, name, body);
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("code", "RESOURCE_NOT_FOUND", "message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("code", "SETTINGS_WRITE_FAILED", "message", "Failed to write MCP settings"));
        }
    }

    private boolean hasConfigBackedSettings(String name) {
        return KNOWLEDGE_SERVICE_MCP.equals(name) || KNOWLEDGE_CLI_MCP.equals(name);
    }

    private Map<String, Object> emptyKnowledgeSettings(String name) {
        Map<String, Object> fallback = new java.util.HashMap<>();
        fallback.put("sourceId", null);
        if (KNOWLEDGE_CLI_MCP.equals(name)) {
            fallback.put("rootDir", null);
        }
        return fallback;
    }
}