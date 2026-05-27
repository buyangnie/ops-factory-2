/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.service.OperationIntelligenceProxyService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Gateway proxy for operation-intelligence endpoints.
 *
 * @author x00000000
 * @since 2026-05-20
 */
public class OperationIntelligenceProxyController {
    private final OperationIntelligenceProxyService proxyService;

    /**
     * Constructs an OperationIntelligenceProxyController.
     *
     * @param proxyService the proxy service
     */
    public OperationIntelligenceProxyController(OperationIntelligenceProxyService proxyService) {
        this.proxyService = proxyService;
    }

    /**
     * Proxies all operation-intelligence requests through the gateway.
     *
     * @param request the request
     * @return the result
     */
    @RequestMapping("/gateway/operation-intelligence/**")
    public ResponseEntity<String> proxy(HttpServletRequest request) {
        return proxyService.proxy(request);
    }
}
