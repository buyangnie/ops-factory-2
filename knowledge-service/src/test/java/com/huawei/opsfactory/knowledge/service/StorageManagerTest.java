/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.huawei.opsfactory.knowledge.config.KnowledgeRuntimeProperties;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageManagerTest {

    @TempDir
    Path tempDir;

    private StorageManager storageManager;

    @BeforeEach
    void setUp() {
        KnowledgeRuntimeProperties runtimeProperties = mock(KnowledgeRuntimeProperties.class);
        when(runtimeProperties.getBaseDir()).thenReturn(tempDir.toString());
        storageManager = new StorageManager(runtimeProperties);
    }

    @Test
    void shouldResolveOriginalFilePath() {
        Path path = storageManager.originalFilePath("src-1", "doc-1", "file.pdf");
        assertThat(path).isEqualTo(tempDir.resolve("upload").resolve("src-1").resolve("doc-1").resolve("original").resolve("file.pdf"));
    }

    @Test
    void shouldResolveArtifactDir() {
        Path path = storageManager.artifactDir("src-1", "doc-1");
        assertThat(path).isEqualTo(tempDir.resolve("artifacts").resolve("src-1").resolve("doc-1"));
    }

    @Test
    void shouldResolveIndexDir() {
        Path path = storageManager.indexDir("lexical-src-1");
        assertThat(path).isEqualTo(tempDir.resolve("indexes").resolve("lexical-src-1"));
    }

    @Test
    void shouldWriteAndReadStringRoundTrip() {
        Path file = tempDir.resolve("test.txt");
        storageManager.writeString(file, "hello world");
        assertThat(storageManager.readString(file)).isEqualTo("hello world");
    }

    @Test
    void shouldTreatNullContentAsEmptyString() {
        Path file = tempDir.resolve("null.txt");
        storageManager.writeString(file, null);
        assertThat(storageManager.readString(file)).isEmpty();
    }

    @Test
    void shouldReturnEmptyStringForNonExistentFile() {
        Path file = tempDir.resolve("missing.txt");
        assertThat(storageManager.readString(file)).isEmpty();
    }

    @Test
    void shouldThrowWhenReadingBytesOfNonExistentFile() {
        Path file = tempDir.resolve("missing.bin");
        assertThatThrownBy(() -> storageManager.readBytes(file))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to read file");
    }

    @Test
    void shouldSaveAndReadBytes() {
        Path file = tempDir.resolve("data.bin");
        byte[] data = new byte[]{1, 2, 3, 4, 5};
        storageManager.save(new ByteArrayInputStream(data), file);
        assertThat(storageManager.readBytes(file)).isEqualTo(data);
    }

    @Test
    void shouldDeleteRecursively() {
        Path dir = tempDir.resolve("to-delete");
        storageManager.writeString(dir.resolve("sub").resolve("file.txt"), "data");
        storageManager.deleteRecursively(dir);
        assertThat(dir).doesNotExist();
    }

    @Test
    void shouldNotThrowWhenDeletingNonExistentPath() {
        Path missing = tempDir.resolve("no-such-dir");
        storageManager.deleteRecursively(missing);
    }
}
