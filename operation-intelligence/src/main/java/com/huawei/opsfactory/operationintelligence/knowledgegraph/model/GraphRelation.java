/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.knowledgegraph.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Knowledge graph relation.
 *
 * @author x00000000
 * @since 2026-05-20
 */
public class GraphRelation {
    private String id;

    private String type;

    private String from;

    private String to;

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
     * Gets the from.
     *
     * @return the from
     */
    public String getFrom() {
        return from;
    }

    /**
     * Sets the from.
     *
     * @param from the from
     */
    public void setFrom(String from) {
        this.from = from;
    }

    /**
     * Gets the to.
     *
     * @return the to
     */
    public String getTo() {
        return to;
    }

    /**
     * Sets the to.
     *
     * @param to the to
     */
    public void setTo(String to) {
        this.to = to;
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
        GraphRelation that = (GraphRelation) obj;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "GraphRelation{id='" + id + "', type='" + type + "', from='" + from + "', to='" + to + "'}";
    }
}
