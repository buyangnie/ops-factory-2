/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.controller;

import com.huawei.opsfactory.operationintelligence.callchainsubgraph.model.CallChainSubgraphRequest;
import com.huawei.opsfactory.operationintelligence.callchainsubgraph.service.CallChainSubgraphService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST API for generating and querying short-lived call chain entity subgraphs.
 *
 * @author x00000000
 * @since 2026-05-27
 */
@RestController
@RequestMapping("/operation-intelligence/call-chain/subgraphs")
public class CallChainSubgraphController {
    private final CallChainSubgraphService callChainSubgraphService;

    /**
     * Constructs a CallChainSubgraphController.
     *
     * @param callChainSubgraphService the call chain subgraph service
     */
    public CallChainSubgraphController(CallChainSubgraphService callChainSubgraphService) {
        this.callChainSubgraphService = callChainSubgraphService;
    }

    /**
     * Generates a call chain subgraph by menuId.
     *
     * @param request the request
     * @return the result
     */
    @PostMapping
    public Map<String, Object> generate(@RequestBody CallChainSubgraphRequest request) {
        return ok("result", callChainSubgraphService.generate(request));
    }

    /**
     * Lists generated call chain subgraph history items.
     *
     * @param ontologyId the ontology id
     * @param envCode the env code
     * @return the results
     */
    @GetMapping
    public Map<String, Object> list(@RequestParam(value = "ontologyId", required = false) String ontologyId,
        @RequestParam(value = "envCode", required = false) String envCode) {
        return ok("result", callChainSubgraphService.list(ontologyId, envCode));
    }

    /**
     * Gets one generated subgraph.
     *
     * @param subgraphId the subgraph id
     * @return the result
     */
    @GetMapping("/{subgraphId}")
    public Map<String, Object> get(@PathVariable("subgraphId") String subgraphId) {
        return ok("result", callChainSubgraphService.get(subgraphId));
    }

    private Map<String, Object> ok(String key, Object value) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put(key, value);
        response.put("error", null);
        return response;
    }
}
