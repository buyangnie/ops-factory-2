/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.common.model.ManagedInstance;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.process.InstanceManager;
import com.huawei.opsfactory.gateway.proxy.GoosedProxy;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.servicecomb.provider.rest.common.RestSchema;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

/**
 * REST controller for managing agent prompt templates through goosed.
 *
 * @author x00000000
 * @since 2026-05-30
 */
@RestController
@RestSchema(schemaId = "promptController")
@RequestMapping("/gateway/agents/{agentId}/config/prompts")
public class PromptController {
    private final InstanceManager instanceManager;

    private final GoosedProxy goosedProxy;

    /**
     * Creates the prompt controller instance.
     *
     * @param instanceManager agent instance lifecycle manager
     * @param goosedProxy HTTP proxy for forwarding requests to goosed processes
     */
    public PromptController(InstanceManager instanceManager, GoosedProxy goosedProxy) {
        this.instanceManager = instanceManager;
        this.goosedProxy = goosedProxy;
    }

    /**
     * Lists all prompt templates for the target agent.
     *
     * @param agentId agent identifier
     * @param request current HTTP request
     * @return proxied goosed response
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> listPrompts(@PathVariable("agentId") String agentId, HttpServletRequest request) {
        ManagedInstance instance = resolveInstance(agentId, request);
        String result =
            goosedProxy.fetchJson(instance.getPort(), HttpMethod.GET, "/config/prompts", null, 30,
                instance.getSecretKey()).block();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    /**
     * Gets an individual prompt template by file name.
     *
     * @param agentId agent identifier
     * @param name prompt file name
     * @param request current HTTP request
     * @return proxied goosed response
     */
    @GetMapping(value = "/{name:.+}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getPrompt(@PathVariable("agentId") String agentId, @PathVariable("name") String name,
        HttpServletRequest request) {
        ManagedInstance instance = resolveInstance(agentId, request);
        String result =
            goosedProxy.fetchJson(instance.getPort(), HttpMethod.GET, promptPath(name), null, 30,
                instance.getSecretKey()).block();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    /**
     * Saves customized content for a prompt template.
     *
     * @param agentId agent identifier
     * @param name prompt file name
     * @param body request body
     * @param request current HTTP request
     * @return proxied goosed response
     */
    @PutMapping(value = "/{name:.+}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> savePrompt(@PathVariable("agentId") String agentId,
        @PathVariable("name") String name, @RequestBody String body, HttpServletRequest request) {
        ManagedInstance instance = resolveInstance(agentId, request);
        String result =
            goosedProxy.fetchJson(instance.getPort(), HttpMethod.PUT, promptPath(name), body, 30,
                instance.getSecretKey()).block();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    /**
     * Resets a prompt template to its default content.
     *
     * @param agentId agent identifier
     * @param name prompt file name
     * @param request current HTTP request
     * @return proxied goosed response
     */
    @DeleteMapping(value = "/{name:.+}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> resetPrompt(@PathVariable("agentId") String agentId,
        @PathVariable("name") String name, HttpServletRequest request) {
        ManagedInstance instance = resolveInstance(agentId, request);
        String result =
            goosedProxy.fetchJson(instance.getPort(), HttpMethod.DELETE, promptPath(name), null, 30,
                instance.getSecretKey()).block();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    private ManagedInstance resolveInstance(String agentId, HttpServletRequest request) {
        String userId = (String) request.getAttribute(UserContextFilter.USER_ID_ATTR);
        return instanceManager.getOrSpawn(agentId, userId).block();
    }

    private String promptPath(String name) {
        return "/config/prompts/" + UriUtils.encodePathSegment(name, StandardCharsets.UTF_8);
    }
}
