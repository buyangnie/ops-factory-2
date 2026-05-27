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
 * Full graph snapshot for one environment.
 *
 * @author x00000000
 * @since 2026-05-20
 */
public class GraphSnapshot {
    private String formatVersion = "1.0";

    private String ontologyId;

    private String envCode;

    private String schemaVersion;

    private String sourceSystem;

    private String importMode = "UPSERT";

    private String snapshotId;

    private String generatedAt;

    private Map<String, Object> metadata = new LinkedHashMap<>();

    private List<GraphEntity> entities = new ArrayList<>();

    private List<GraphRelation> relations = new ArrayList<>();

    private List<GraphObservation> observations = new ArrayList<>();

    /**
     * Gets the formatVersion.
     *
     * @return the formatVersion
     */
    public String getFormatVersion() {
        return formatVersion;
    }

    /**
     * Sets the formatVersion.
     *
     * @param formatVersion the formatVersion
     */
    public void setFormatVersion(String formatVersion) {
        this.formatVersion = formatVersion;
    }

    /**
     * Gets the ontologyId.
     *
     * @return the ontologyId
     */
    public String getOntologyId() {
        return ontologyId;
    }

    /**
     * Sets the ontologyId.
     *
     * @param ontologyId the ontologyId
     */
    public void setOntologyId(String ontologyId) {
        this.ontologyId = ontologyId;
    }

    /**
     * Gets the envCode.
     *
     * @return the envCode
     */
    public String getEnvCode() {
        return envCode;
    }

    /**
     * Sets the envCode.
     *
     * @param envCode the envCode
     */
    public void setEnvCode(String envCode) {
        this.envCode = envCode;
    }

    /**
     * Gets the schemaVersion.
     *
     * @return the schemaVersion
     */
    public String getSchemaVersion() {
        return schemaVersion;
    }

    /**
     * Sets the schemaVersion.
     *
     * @param schemaVersion the schemaVersion
     */
    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    /**
     * Gets the sourceSystem.
     *
     * @return the sourceSystem
     */
    public String getSourceSystem() {
        return sourceSystem;
    }

    /**
     * Sets the sourceSystem.
     *
     * @param sourceSystem the sourceSystem
     */
    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    /**
     * Gets the importMode.
     *
     * @return the importMode
     */
    public String getImportMode() {
        return importMode;
    }

    /**
     * Sets the importMode.
     *
     * @param importMode the importMode
     */
    public void setImportMode(String importMode) {
        this.importMode = importMode;
    }

    /**
     * Gets the snapshotId.
     *
     * @return the snapshotId
     */
    public String getSnapshotId() {
        return snapshotId;
    }

    /**
     * Sets the snapshotId.
     *
     * @param snapshotId the snapshotId
     */
    public void setSnapshotId(String snapshotId) {
        this.snapshotId = snapshotId;
    }

    /**
     * Gets the generatedAt.
     *
     * @return the generatedAt
     */
    public String getGeneratedAt() {
        return generatedAt;
    }

    /**
     * Sets the generatedAt.
     *
     * @param generatedAt the generatedAt
     */
    public void setGeneratedAt(String generatedAt) {
        this.generatedAt = generatedAt;
    }

    /**
     * Gets the metadata.
     *
     * @return the metadata
     */
    public Map<String, Object> getMetadata() {
        return new LinkedHashMap<>(metadata);
    }

    /**
     * Sets the metadata.
     *
     * @param metadata the metadata
     */
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }

    /**
     * Gets the entities.
     *
     * @return the entities
     */
    public List<GraphEntity> getEntities() {
        return new ArrayList<>(entities);
    }

    /**
     * Sets the entities.
     *
     * @param entities the entities
     */
    public void setEntities(List<GraphEntity> entities) {
        this.entities = entities == null ? new ArrayList<>() : new ArrayList<>(entities);
    }

    /**
     * Gets the relations.
     *
     * @return the relations
     */
    public List<GraphRelation> getRelations() {
        return new ArrayList<>(relations);
    }

    /**
     * Sets the relations.
     *
     * @param relations the relations
     */
    public void setRelations(List<GraphRelation> relations) {
        this.relations = relations == null ? new ArrayList<>() : new ArrayList<>(relations);
    }

    /**
     * Gets the observations.
     *
     * @return the observations
     */
    public List<GraphObservation> getObservations() {
        return new ArrayList<>(observations);
    }

    /**
     * Sets the observations.
     *
     * @param observations the observations
     */
    public void setObservations(List<GraphObservation> observations) {
        this.observations = observations == null ? new ArrayList<>() : new ArrayList<>(observations);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        GraphSnapshot that = (GraphSnapshot) obj;
        return Objects.equals(ontologyId, that.ontologyId) && Objects.equals(envCode, that.envCode)
            && Objects.equals(snapshotId, that.snapshotId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ontologyId, envCode, snapshotId);
    }

    @Override
    public String toString() {
        return "GraphSnapshot{ontologyId='" + ontologyId + "', envCode='" + envCode
            + "', snapshotId='" + snapshotId + "'}";
    }
}
