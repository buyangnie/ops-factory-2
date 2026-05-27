/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.knowledgegraph.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Knowledge graph observation.
 *
 * @author x00000000
 * @since 2026-05-20
 */
public class GraphObservation {
    private String id;

    private String entityId;

    private String observedAt;

    private String category;

    private String name;

    private String severity = "unknown";

    private Object value;

    private String unit;

    private Map<String, Object> properties = new LinkedHashMap<>();

    private GraphSource source = new GraphSource();

    /**
     * Gets the id.
     *
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the id.
     *
     * @param id the id
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Gets the entityId.
     *
     * @return the entityId
     */
    public String getEntityId() {
        return entityId;
    }

    /**
     * Sets the entityId.
     *
     * @param entityId the entityId
     */
    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    /**
     * Gets the observedAt.
     *
     * @return the observedAt
     */
    public String getObservedAt() {
        return observedAt;
    }

    /**
     * Sets the observedAt.
     *
     * @param observedAt the observedAt
     */
    public void setObservedAt(String observedAt) {
        this.observedAt = observedAt;
    }

    /**
     * Gets the category.
     *
     * @return the category
     */
    public String getCategory() {
        return category;
    }

    /**
     * Sets the category.
     *
     * @param category the category
     */
    public void setCategory(String category) {
        this.category = category;
    }

    /**
     * Gets the name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name.
     *
     * @param name the name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the severity.
     *
     * @return the severity
     */
    public String getSeverity() {
        return severity;
    }

    /**
     * Sets the severity.
     *
     * @param severity the severity
     */
    public void setSeverity(String severity) {
        this.severity = severity;
    }

    /**
     * Gets the value.
     *
     * @return the value
     */
    public Object getValue() {
        return value;
    }

    /**
     * Sets the value.
     *
     * @param value the value
     */
    public void setValue(Object value) {
        this.value = value;
    }

    /**
     * Gets the unit.
     *
     * @return the unit
     */
    public String getUnit() {
        return unit;
    }

    /**
     * Sets the unit.
     *
     * @param unit the unit
     */
    public void setUnit(String unit) {
        this.unit = unit;
    }

    /**
     * Gets the properties.
     *
     * @return the properties
     */
    public Map<String, Object> getProperties() {
        return new LinkedHashMap<>(properties);
    }

    /**
     * Sets the properties.
     *
     * @param properties the properties
     */
    public void setProperties(Map<String, Object> properties) {
        this.properties = properties == null ? new LinkedHashMap<>() : new LinkedHashMap<>(properties);
    }

    /**
     * Gets the source.
     *
     * @return the source
     */
    public GraphSource getSource() {
        return source;
    }

    /**
     * Sets the source.
     *
     * @param source the source
     */
    public void setSource(GraphSource source) {
        this.source = source == null ? new GraphSource() : source;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        GraphObservation that = (GraphObservation) obj;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "GraphObservation{id='" + id + "', entityId='" + entityId + "', severity='" + severity + "'}";
    }
}
