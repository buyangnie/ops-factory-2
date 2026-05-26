/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.huawei.opsfactory.knowledge.config.KnowledgeProperties;
import com.huawei.opsfactory.knowledge.config.KnowledgeRuntimeProperties;
import com.huawei.opsfactory.knowledge.repository.ChunkRepository;
import com.huawei.opsfactory.knowledge.repository.ProfileRepository;
import com.huawei.opsfactory.knowledge.repository.SourceRepository;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LexicalIndexServiceTest {

    @TempDir
    Path tempDir;

    private LexicalIndexService service;
    private ProfileBootstrapService profileBootstrapService;

    private SearchService.SearchableChunk chunk1;
    private SearchService.SearchableChunk chunk2;

    @BeforeEach
    void setUp() {
        KnowledgeProperties properties = new KnowledgeProperties();
        KnowledgeRuntimeProperties runtimeProperties = mock(KnowledgeRuntimeProperties.class);
        when(runtimeProperties.getBaseDir()).thenReturn(tempDir.toString());

        StorageManager storageManager = new StorageManager(runtimeProperties);
        ChunkRepository chunkRepository = mock(ChunkRepository.class);
        SourceRepository sourceRepository = mock(SourceRepository.class);
        ProfileRepository profileRepository = mock(ProfileRepository.class);

        when(profileRepository.findIndexByName(anyString())).thenReturn(Optional.empty());
        when(profileRepository.findRetrievalByName(anyString())).thenReturn(Optional.empty());

        profileBootstrapService = new ProfileBootstrapService(profileRepository, properties);
        service = new LexicalIndexService(
            storageManager, properties, chunkRepository, sourceRepository,
            profileRepository, profileBootstrapService
        );

        chunk1 = new SearchService.SearchableChunk(
            "chk-1", "doc-1", "src-1", "Machine Learning",
            List.of("ML"), List.of("algorithm", "model"),
            "Machine learning is a branch of artificial intelligence.", "markdown", 1, 1, 1, "ACTIVE", "user"
        );
        chunk2 = new SearchService.SearchableChunk(
            "chk-2", "doc-1", "src-1", "Deep Learning",
            List.of("DL"), List.of("neural", "network"),
            "Deep learning uses multi-layered neural networks.", "markdown", 2, 2, 2, "ACTIVE", "user"
        );
    }

    @Test
    void shouldReturnEmptyWhenQueryIsBlank() {
        assertThat(service.search(null, List.of(chunk1), 10)).isEmpty();
        assertThat(service.search("", List.of(chunk1), 10)).isEmpty();
        assertThat(service.search("   ", List.of(chunk1), 10)).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenTopKIsZero() {
        assertThat(service.search("machine learning", List.of(chunk1), 0)).isEmpty();
        assertThat(service.search("machine learning", List.of(chunk1), -1)).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenChunksIsEmpty() {
        assertThat(service.search("query", List.of(), 10)).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenChunksIsNull() {
        assertThat(service.search("query", null, 10)).isEmpty();
    }

    @Test
    void shouldRebuildIndexAndSearch() {
        service.rebuildIndex(List.of(chunk1, chunk2));

        List<LexicalIndexService.LexicalHit> hits = service.search("machine learning", List.of(chunk1, chunk2), 10);

        assertThat(hits).isNotEmpty();
        assertThat(hits.stream().map(LexicalIndexService.LexicalHit::chunkId).toList()).contains("chk-1");
    }

    @Test
    void shouldUpsertChunksIntoExistingIndex() {
        service.rebuildIndex(List.of(chunk1));

        SearchService.SearchableChunk updatedChunk = new SearchService.SearchableChunk(
            "chk-1", "doc-1", "src-1", "Updated Title",
            List.of("ML"), List.of("updated"),
            "Updated text about quantum computing algorithms.", "markdown", 1, 1, 1, "ACTIVE", "user"
        );
        service.upsertChunks(List.of(updatedChunk));

        List<LexicalIndexService.LexicalHit> hits = service.search("quantum computing", List.of(updatedChunk), 10);
        assertThat(hits).isNotEmpty();
    }

    @Test
    void shouldDeleteChunkFromIndex() {
        service.rebuildIndex(List.of(chunk1, chunk2));
        service.deleteChunk("src-1", "chk-1");

        List<LexicalIndexService.LexicalHit> hits = service.search("machine learning", List.of(chunk2), 10);
        assertThat(hits.stream().map(LexicalIndexService.LexicalHit::chunkId)).doesNotContain("chk-1");
    }

    @Test
    void shouldDeleteDocumentFromIndex() {
        service.rebuildIndex(List.of(chunk1, chunk2));
        service.deleteDocument("src-1", "doc-1");

        List<LexicalIndexService.LexicalHit> hits = service.search("learning", List.of(), 10);
        assertThat(hits).isEmpty();
    }

    @Test
    void shouldDeleteSourceIndexEntirely() {
        service.rebuildIndex(List.of(chunk1, chunk2));
        service.deleteSource("src-1");

        assertThat(service.search("learning", List.of(), 10)).isEmpty();
    }

    @Test
    void shouldUpsertChunksEarlyReturnWhenNullOrEmpty() {
        service.upsertChunks(null);
        service.upsertChunks(List.of());
    }
}
