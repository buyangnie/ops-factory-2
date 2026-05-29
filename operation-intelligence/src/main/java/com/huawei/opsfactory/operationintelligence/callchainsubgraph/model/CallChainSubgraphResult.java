/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.callchainsubgraph.model;

import com.huawei.opsfactory.operationintelligence.knowledgegraph.model.GraphSnapshot;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generated call chain entity subgraph payload.
 *
 * @author x00000000
 * @since 2026-05-27
 */
public class CallChainSubgraphResult {
    private String subgraphId;

    private String menuId;

    private String envCode;

    private String solutionType;

    private String ontologyId;

    private String generatedAt;

    private String expiresAt;

    private GraphSnapshot graph;

    private Map<String, Object> summary = new LinkedHashMap<>();

    /**
     * Gets the subgraph id.
     *
     * @return the result
     */
    public String getSubgraphId() {
        return subgraphId;
    }

    /**
     * Sets the subgraph id.
     *
     * @param subgraphId the subgraphId
     */
    public void setSubgraphId(String subgraphId) {
        this.subgraphId = subgraphId;
    }

    /**
     * Gets the menu id.
     *
     * @return the result
     */
    public String getMenuId() {
        return menuId;
    }

    /**
     * Sets the menu id.
     *
     * @param menuId the menuId
     */
    public void setMenuId(String menuId) {
        this.menuId = menuId;
    }

    /**
     * Gets the environment code.
     *
     * @return the result
     */
    public String getEnvCode() {
        return envCode;
    }

    /**
     * Sets the environment code.
     *
     * @param envCode the envCode
     */
    public void setEnvCode(String envCode) {
        this.envCode = envCode;
    }

    /**
     * Gets the solution type.
     *
     * @return the result
     */
    public String getSolutionType() {
        return solutionType;
    }

    /**
     * Sets the solution type.
     *
     * @param solutionType the solutionType
     */
    public void setSolutionType(String solutionType) {
        this.solutionType = solutionType;
    }

    /**
     * Gets the ontology id.
     *
     * @return the result
     */
    public String getOntologyId() {
        return ontologyId;
    }

    /**
     * Sets the ontology id.
     *
     * @param ontologyId the ontologyId
     */
    public void setOntologyId(String ontologyId) {
        this.ontologyId = ontologyId;
    }

    /**
     * Gets the generated at.
     *
     * @return the result
     */
    public String getGeneratedAt() {
        return generatedAt;
    }

    /**
     * Sets the generated at.
     *
     * @param generatedAt the generatedAt
     */
    public void setGeneratedAt(String generatedAt) {
        this.generatedAt = generatedAt;
    }

    /**
     * Gets the expires at.
     *
     * @return the result
     */
    public String getExpiresAt() {
        return expiresAt;
    }

    /**
     * Sets the expires at.
     *
     * @param expiresAt the expiresAt
     */
    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }

    /**
     * Gets the graph snapshot.
     *
     * @return the result
     */
    public GraphSnapshot getGraph() {
        return graph;
    }

    /**
     * Sets the graph snapshot.
     *
     * @param graph the graph
     */
    public void setGraph(GraphSnapshot graph) {
        this.graph = graph;
    }

    /**
     * Gets the summary.
     *
     * @return the result
     */
    public Map<String, Object> getSummary() {
        return new LinkedHashMap<>(summary);
    }

    /**
     * Sets the summary.
     *
     * @param summary the summary
     */
    public void setSummary(Map<String, Object> summary) {
        this.summary = summary == null ? new LinkedHashMap<>() : new LinkedHashMap<>(summary);
    }
}
