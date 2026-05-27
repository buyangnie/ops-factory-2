/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.knowledgegraph.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Entity type definition in a graph ontology.
 *
 * @author x00000000
 * @since 2026-05-22
 */
public class EntityTypeDefinition {
    private String type;

    private List<String> requiredProperties = new ArrayList<>();

    private List<String> optionalProperties = new ArrayList<>();

    /**
     * Gets the type.
     *
     * @return the type
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the type.
     *
     * @param type the type
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Gets the requiredProperties.
     *
     * @return the requiredProperties
     */
    public List<String> getRequiredProperties() {
        return new ArrayList<>(requiredProperties);
    }

    /**
     * Sets the requiredProperties.
     *
     * @param requiredProperties the requiredProperties
     */
    public void setRequiredProperties(List<String> requiredProperties) {
        this.requiredProperties = requiredProperties == null ? new ArrayList<>() : new ArrayList<>(requiredProperties);
    }

    /**
     * Gets the optionalProperties.
     *
     * @return the optionalProperties
     */
    public List<String> getOptionalProperties() {
        return new ArrayList<>(optionalProperties);
    }

    /**
     * Sets the optionalProperties.
     *
     * @param optionalProperties the optionalProperties
     */
    public void setOptionalProperties(List<String> optionalProperties) {
        this.optionalProperties = optionalProperties == null ? new ArrayList<>() : new ArrayList<>(optionalProperties);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        EntityTypeDefinition that = (EntityTypeDefinition) obj;
        return Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type);
    }

    @Override
    public String toString() {
        return "EntityTypeDefinition{type='" + type + "'}";
    }
}