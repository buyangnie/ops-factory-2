/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.process.InstanceManager;
import com.huawei.opsfactory.gateway.proxy.GoosedProxy;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.servicecomb.provider.rest.common.RestSchema;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proxy controller: forwards agent status requests to the goosed instance.
 */

@RestController
@RestSchema(schemaId = "catchAllProxyController")
@RequestMapping(value = "/gateway")
@Order(999)
public class CatchAllProxyController {
    private final InstanceManager instanceManager;

    private final GoosedProxy goosedProxy;

    /**
     * Creates the proxy controller instance.
     *
     * @param instanceManager manages goosed process instances
     * @param goosedProxy forwards requests to goosed instances
     */
    public CatchAllProxyController(InstanceManager instanceManager, GoosedProxy goosedProxy) {
        this.instanceManager = instanceManager;
        this.goosedProxy = goosedProxy;
    }

    /**
     * Proxies status requests to goosed instance.
     *
     * @param agentId agent identifier
     * @param request current HTTP request
     * @return the response from goosed instance
     */
    @GetMapping("/agents/{agentId}/status")
    public String proxyStatus(@PathVariable("agentId") String agentId, HttpServletRequest request) {
        String userId = (String) request.getAttribute(UserContextFilter.USER_ID_ATTR);

        String query = request.getQueryString();
        String proxyTarget = "/status";
        if (query != null && !query.isEmpty()) {
            proxyTarget = "/status?" + query;
        }

        var instance = instanceManager.getOrSpawn(agentId, userId).block();
        return goosedProxy.fetchJson(instance.getPort(), HttpMethod.GET, proxyTarget, null, 30, instance.getSecretKey())
            .block();
    }

    /**
     * Proxies system_info requests to goosed instance.
     *
     * @param agentId agent identifier
     * @param request current HTTP request
     * @return the response from goosed instance
     */
    @GetMapping("/agents/{agentId}/system_info")
    public ResponseEntity<String> proxySystemInfo(@PathVariable("agentId") String agentId, HttpServletRequest request) {
        String userId = (String) request.getAttribute(UserContextFilter.USER_ID_ATTR);

        var instance = instanceManager.getOrSpawn(agentId, userId).block();
        String body =
            goosedProxy.fetchJson(instance.getPort(), HttpMethod.GET, "/system_info", null, 30, instance.getSecretKey())
                .block();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(MediaType.APPLICATION_JSON_VALUE)).body(body);
    }
}
