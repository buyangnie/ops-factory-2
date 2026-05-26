/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.opsfactory.knowledge.config.KnowledgeProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChunkingServiceTest {

    private KnowledgeProperties properties;
    private ChunkingService service;

    @BeforeEach
    void setUp() {
        properties = new KnowledgeProperties();
        service = new ChunkingService(properties);
    }

    @Test
    void shouldReturnSingleChunkWhenTextIsNull() {
        List<ChunkingService.ChunkDraft> drafts = service.chunk("Title", null, null);

        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).ordinal()).isEqualTo(1);
        assertThat(drafts.get(0).text()).isEmpty();
    }

    @Test
    void shouldReturnSingleChunkWhenTextIsEmpty() {
        List<ChunkingService.ChunkDraft> drafts = service.chunk("Title", "", null);

        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).text()).isEmpty();
    }

    @Test
    void shouldReturnSingleChunkWhenTextBelowTokenThreshold() {
        List<ChunkingService.ChunkDraft> drafts = service.chunk("Title", "short text", null);

        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).text()).isEqualTo("short text");
        assertThat(drafts.get(0).ordinal()).isEqualTo(1);
    }

    @Test
    void shouldSplitIntoMultipleChunksWhenExceedingThreshold() {
        properties.getChunking().setTargetTokens(5);

        String text = "alpha beta gamma delta\n\nepsilon zeta eta theta\n\niota kappa lambda mu";
        List<ChunkingService.ChunkDraft> drafts = service.chunk("Doc", text, null);

        assertThat(drafts.size()).isGreaterThan(1);
        assertThat(drafts.get(0).ordinal()).isEqualTo(1);
    }

    @Test
    void shouldAssignCorrectOrdinals() {
        properties.getChunking().setTargetTokens(3);

        String text = "first block\n\nsecond block\n\nthird block";
        List<ChunkingService.ChunkDraft> drafts = service.chunk("Doc", text, null);

        for (int i = 0; i < drafts.size(); i++) {
            assertThat(drafts.get(i).ordinal()).isEqualTo(i + 1);
        }
    }

    @Test
    void shouldProduceTitlePathFromTitle() {
        List<ChunkingService.ChunkDraft> drafts = service.chunk("My Title", "some text", null);

        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).titlePath()).containsExactly("My Title");
    }

    @Test
    void shouldProduceEmptyTitlePathWhenTitleIsBlank() {
        List<ChunkingService.ChunkDraft> drafts = service.chunk("   ", "some text", null);

        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).titlePath()).isEmpty();
    }

    @Test
    void shouldProduceEmptyTitlePathWhenTitleIsNull() {
        List<ChunkingService.ChunkDraft> drafts = service.chunk(null, "some text", null);

        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).titlePath()).isEmpty();
    }

    @Test
    void shouldExtractKeywordsInEachChunk() {
        String text = "Artificial intelligence machine learning deep learning neural networks";
        List<ChunkingService.ChunkDraft> drafts = service.chunk("AI", text, null);

        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).keywords()).isNotEmpty();
    }

    @Test
    void shouldAlwaysProduceAtLeastOneChunk() {
        List<ChunkingService.ChunkDraft> drafts = service.chunk(null, "", null);

        assertThat(drafts).isNotEmpty();
    }

    @Test
    void shouldPopulateTokenCountAndTextLength() {
        String text = "hello world";
        List<ChunkingService.ChunkDraft> drafts = service.chunk("Title", text, null);

        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).textLength()).isEqualTo(text.length());
        assertThat(drafts.get(0).tokenCount()).isGreaterThan(0);
    }
}
