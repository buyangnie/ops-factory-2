/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.knowledgegraph.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class KnowledgeGraphStorePathSafetyTest {
    @TempDir
    Path tempDir;

    @Test
    void graphSnapshotStoreResolveRootStaysUnderDataRoot() {
        OperationIntelligenceProperties properties = createProperties("knowledge-graph");
        GraphSnapshotStore store = new GraphSnapshotStore(properties);

        Path resolved = store.resolveRoot();

        assertEquals(tempDir.resolve("knowledge-graph").normalize(), resolved);
    }

    @Test
    void graphSnapshotStoreRejectsAbsoluteKnowledgeGraphDataDir() {
        OperationIntelligenceProperties properties = createProperties(tempDir.resolve("outside").toString());
        GraphSnapshotStore store = new GraphSnapshotStore(properties);

        IllegalStateException ex = assertThrows(IllegalStateException.class, store::resolveRoot);

        assertTrue(ex.getMessage().contains("relative path"));
    }

    @Test
    void graphSnapshotStoreDeleteSnapshotRejectsEscapingKnowledgeGraphDataDir() {
        OperationIntelligenceProperties properties = createProperties("../outside");
        GraphSnapshotStore store = new GraphSnapshotStore(properties);

        IllegalStateException ex =
            assertThrows(IllegalStateException.class, () -> store.deleteSnapshot("ontology", "prod"));

        assertTrue(ex.getMessage().contains("stay within data root"));
    }

    @Test
    void graphOntologyStoreRejectsEscapingKnowledgeGraphDataDir() {
        OperationIntelligenceProperties properties = createProperties("../outside");
        GraphOntologyStore store = new GraphOntologyStore(properties);

        IllegalStateException ex = assertThrows(IllegalStateException.class, store::loadAll);

        assertTrue(ex.getMessage().contains("stay within data root"));
    }

    private OperationIntelligenceProperties createProperties(String dataDir) {
        OperationIntelligenceProperties properties = new OperationIntelligenceProperties();
        properties.setDataRoot(tempDir.toString());
        properties.getKnowledgeGraph().setDataDir(dataDir);
        return properties;
    }
}
