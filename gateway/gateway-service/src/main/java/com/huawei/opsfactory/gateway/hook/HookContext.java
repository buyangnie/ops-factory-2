/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.hook;

import java.util.HashMap;
import java.util.Map;

/**
 * Carries the request body and contextual state through the request hook pipeline.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class HookContext {
    private String body;

    private final String agentId;

    private final String userId;

    private final Map<String, Object> state = new HashMap<>();

    /**
     * Creates the hook context instance.
     *
     * @author x00000000
     * @since 2026-05-09
     */
    public HookContext(String body, String agentId, String userId) {
        this.body = body;
        this.agentId = agentId;
        this.userId = userId;
    }

    /**
     * Gets the request body.
     *
     * @return the result
     */
    public String getBody() {
        return body;
    }

    /**
     * Sets the request body.
     *
     * @param body the body parameter
     */
    public void setBody(String body) {
        this.body = body;
    }

    /**
     * Gets the agent identifier.
     *
     * @return the result
     */
    public String getAgentId() {
        return agentId;
    }

    /**
     * Gets the user identifier.
     *
     * @return the result
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Gets the mutable state map shared across hooks.
     *
     * @return the result
     */
    public Map<String, Object> getState() {
        return state;
    }
}
