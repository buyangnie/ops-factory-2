/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.callchainsubgraph.model;

/**
 * Request for generating a call chain entity subgraph.
 *
 * @author x00000000
 * @since 2026-05-27
 */
public class CallChainSubgraphRequest {
    private String menuId;

    private String envCode;

    private String solutionType;

    private String mode;

    private String ontologyId;

    private Long startTime;

    private Long endTime;

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
     * Gets the query mode.
     *
     * @return the result
     */
    public String getMode() {
        return mode;
    }

    /**
     * Sets the query mode.
     *
     * @param mode the mode
     */
    public void setMode(String mode) {
        this.mode = mode;
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
     * Gets the start time.
     *
     * @return the result
     */
    public Long getStartTime() {
        return startTime;
    }

    /**
     * Sets the start time.
     *
     * @param startTime the startTime
     */
    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    /**
     * Gets the end time.
     *
     * @return the result
     */
    public Long getEndTime() {
        return endTime;
    }

    /**
     * Sets the end time.
     *
     * @param endTime the endTime
     */
    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }
}
