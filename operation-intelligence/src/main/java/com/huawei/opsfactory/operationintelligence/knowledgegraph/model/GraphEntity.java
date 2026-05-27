/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.knowledgegraph.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Knowledge graph entity.
 *
 * @author x00000000
 * @since 2026-05-20
 */
public class GraphEntity {
    private String id;

    private String type;

    private String name;

    private String displayName;

    private String status = "Unknown";

    private List<String> labels = List.of();

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
     * Gets the displayName.
     *
     * @return the displayName
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Sets the displayName.
     *
     * @param displayName the displayName
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Gets the status.
     *
     * @return the status
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sets the status.
     *
     * @param status the status
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Gets the labels.
     *
     * @return the labels
     */
    public List<String> getLabels() {
        return new ArrayList<>(labels);
    }

    /**
     * Sets the labels.
     *
     * @param labels the labels
     */
    public void setLabels(List<String> labels) {
        this.labels = labels == null ? List.of() : new ArrayList<>(labels);
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
        GraphEntity that = (GraphEntity) obj;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "GraphEntity{id='" + id + "', type='" + type + "', name='" + name + "'}";
    }
}
