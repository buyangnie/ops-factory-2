/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.callchainsubgraph.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight history item for a generated call chain subgraph.
 *
 * @author x00000000
 * @since 2026-05-29
 */
public class CallChainSubgraphHistoryItem {
    private String subgraphId;

    private String menuId;

    private String envCode;

    private String solutionType;

    private String ontologyId;

    private String generatedAt;

    private String expiresAt;

    private Map<String, Object> summary = new LinkedHashMap<>();

    /**
     * Gets the subgraph id.
     *
     * @return the subgraph id
     */
    public String getSubgraphId() {
        return subgraphId;
    }

    /**
     * Sets the subgraph id.
     *
     * @param subgraphId the subgraph id
     */
    public void setSubgraphId(String subgraphId) {
        this.subgraphId = subgraphId;
    }

    /**
     * Gets the menu id.
     *
     * @return the menu id
     */
    public String getMenuId() {
        return menuId;
    }

    /**
     * Sets the menu id.
     *
     * @param menuId the menu id
     */
    public void setMenuId(String menuId) {
        this.menuId = menuId;
    }

    /**
     * Gets the env code.
     *
     * @return the env code
     */
    public String getEnvCode() {
        return envCode;
    }

    /**
     * Sets the env code.
     *
     * @param envCode the env code
     */
    public void setEnvCode(String envCode) {
        this.envCode = envCode;
    }

    /**
     * Gets the solution type.
     *
     * @return the solution type
     */
    public String getSolutionType() {
        return solutionType;
    }

    /**
     * Sets the solution type.
     *
     * @param solutionType the solution type
     */
    public void setSolutionType(String solutionType) {
        this.solutionType = solutionType;
    }

    /**
     * Gets the ontology id.
     *
     * @return the ontology id
     */
    public String getOntologyId() {
        return ontologyId;
    }

    /**
     * Sets the ontology id.
     *
     * @param ontologyId the ontology id
     */
    public void setOntologyId(String ontologyId) {
        this.ontologyId = ontologyId;
    }

    /**
     * Gets the generated at.
     *
     * @return the generated at
     */
    public String getGeneratedAt() {
        return generatedAt;
    }

    /**
     * Sets the generated at.
     *
     * @param generatedAt the generated at
     */
    public void setGeneratedAt(String generatedAt) {
        this.generatedAt = generatedAt;
    }

    /**
     * Gets the expires at.
     *
     * @return the expires at
     */
    public String getExpiresAt() {
        return expiresAt;
    }

    /**
     * Sets the expires at.
     *
     * @param expiresAt the expires at
     */
    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }

    /**
     * Gets the summary.
     *
     * @return the summary
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
