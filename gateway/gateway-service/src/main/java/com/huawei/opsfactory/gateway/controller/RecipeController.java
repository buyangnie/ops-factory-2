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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for recipe template management through goosed.
 *
 * @author x00000000
 * @since 2026-05-30
 */
@RestController
@RestSchema(schemaId = "recipeController")
@RequestMapping("/gateway/agents/{agentId}/recipes")
public class RecipeController {
    private final InstanceManager instanceManager;

    private final GoosedProxy goosedProxy;

    /**
     * Creates the recipe controller instance.
     *
     * @param instanceManager agent instance lifecycle manager
     * @param goosedProxy HTTP proxy for forwarding requests to goosed processes
     */
    public RecipeController(InstanceManager instanceManager, GoosedProxy goosedProxy) {
        this.instanceManager = instanceManager;
        this.goosedProxy = goosedProxy;
    }

    /**
     * Saves a recipe manifest in goosed.
     *
     * @param agentId agent identifier
     * @param body request body
     * @param request current HTTP request
     * @return proxied goosed response
     */
    @PostMapping(value = "/save", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> saveRecipe(@PathVariable("agentId") String agentId, @RequestBody String body,
        HttpServletRequest request) {
        ManagedInstance instance = resolveInstance(agentId, request);
        String result =
            goosedProxy.fetchJson(instance.getPort(), HttpMethod.POST, "/recipes/save", body, 30,
                instance.getSecretKey()).block();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    /**
     * Lists recipe manifests from goosed.
     *
     * @param agentId agent identifier
     * @param request current HTTP request
     * @return proxied goosed response
     */
    @GetMapping(value = "/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> listRecipes(@PathVariable("agentId") String agentId, HttpServletRequest request) {
        ManagedInstance instance = resolveInstance(agentId, request);
        String result =
            goosedProxy.fetchJson(instance.getPort(), HttpMethod.GET, "/recipes/list", null, 30,
                instance.getSecretKey()).block();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    private ManagedInstance resolveInstance(String agentId, HttpServletRequest request) {
        String userId = (String) request.getAttribute(UserContextFilter.USER_ID_ATTR);
        return instanceManager.getOrSpawn(agentId, userId).block();
    }
}
