/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.knowledgegraph.model;

/**
 * Source information for graph data.
 *
 * @author x00000000
 * @since 2026-05-20
 */
public class GraphSource {
    private String system;

    private String externalId;

    /**
     * Gets the system.
     *
     * @return the system
     */
    public String getSystem() {
        return system;
    }

    /**
     * Sets the system.
     *
     * @param system the system
     */
    public void setSystem(String system) {
        this.system = system;
    }

    /**
     * Gets the externalId.
     *
     * @return the externalId
     */
    public String getExternalId() {
        return externalId;
    }

    /**
     * Sets the externalId.
     *
     * @param externalId the externalId
     */
    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }
}
