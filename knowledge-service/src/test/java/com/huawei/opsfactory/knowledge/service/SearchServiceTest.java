/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.huawei.opsfactory.knowledge.config.KnowledgeProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SearchServiceTest {

    private EmbeddingService embeddingService;
    private LexicalIndexService lexicalIndexService;
    private VectorIndexService vectorIndexService;
    private SearchService searchService;

    private SearchService.SearchableChunk chunk1;
    private SearchService.SearchableChunk chunk2;

    @BeforeEach
    void setUp() {
        KnowledgeProperties properties = new KnowledgeProperties();
        embeddingService = mock(EmbeddingService.class);
        lexicalIndexService = mock(LexicalIndexService.class);
        vectorIndexService = mock(VectorIndexService.class);
        searchService = new SearchService(properties, embeddingService, lexicalIndexService, vectorIndexService);

        chunk1 = new SearchService.SearchableChunk(
            "chk-1", "doc-1", "src-1", "Title A", List.of("A"), List.of("kw1"),
            "text one", "text one", 1, 1, 1, "ACTIVE", "user"
        );
        chunk2 = new SearchService.SearchableChunk(
            "chk-2", "doc-1", "src-1", "Title B", List.of("B"), List.of("kw2"),
            "text two", "text two", 2, 2, 2, "ACTIVE", "user"
        );
    }

    private SearchService.SearchOptions defaultOptions() {
        return new SearchService.SearchOptions("hybrid", 10, 10, 10, 60, null);
    }

    @Test
    void shouldReturnEmptyWhenQueryIsBlank() {
        List<SearchService.SearchMatch> result = searchService.search(
            List.of(chunk1), "", defaultOptions()
        );
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenQueryIsNull() {
        List<SearchService.SearchMatch> result = searchService.search(
            List.of(chunk1), null, defaultOptions()
        );
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenQueryIsWhitespace() {
        List<SearchService.SearchMatch> result = searchService.search(
            List.of(chunk1), "   ", defaultOptions()
        );
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnLexicalMatchesInDefaultMode() {
        when(lexicalIndexService.search(eq("query"), any(), eq(10)))
            .thenReturn(List.of(new LexicalIndexService.LexicalHit("chk-1", 0.9, List.of("text"))));
        when(embeddingService.embedQuery("query")).thenReturn(List.of());
        when(vectorIndexService.search(any(), any(), anyInt())).thenReturn(List.of());

        SearchService.SearchOptions options = new SearchService.SearchOptions("lexical", 10, 10, 10, 60, null);
        List<SearchService.SearchMatch> result = searchService.search(List.of(chunk1), "query", options);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).chunk().id()).isEqualTo("chk-1");
        assertThat(result.get(0).lexicalScore()).isEqualTo(0.9);
    }

    @Test
    void shouldReturnSemanticMatchesInSemanticMode() {
        when(lexicalIndexService.search(eq("query"), any(), eq(10)))
            .thenReturn(List.of());
        when(embeddingService.embedQuery("query")).thenReturn(List.of());
        when(vectorIndexService.search(any(), any(), anyInt()))
            .thenReturn(List.of(new VectorIndexService.SemanticHit("chk-1", 0.85)));

        SearchService.SearchOptions options = new SearchService.SearchOptions("semantic", 10, 10, 10, 60, null);
        List<SearchService.SearchMatch> result = searchService.search(List.of(chunk1), "query", options);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).chunk().id()).isEqualTo("chk-1");
        assertThat(result.get(0).semanticScore()).isEqualTo(0.85);
    }

    @Test
    void shouldApplyHybridRrfFusionInHybridMode() {
        when(lexicalIndexService.search(eq("query"), any(), eq(10)))
            .thenReturn(List.of(new LexicalIndexService.LexicalHit("chk-1", 0.9, List.of("text"))));
        when(embeddingService.embedQuery("query")).thenReturn(List.of());
        when(vectorIndexService.search(any(), any(), anyInt()))
            .thenReturn(List.of(new VectorIndexService.SemanticHit("chk-2", 0.8)));

        List<SearchService.SearchMatch> result = searchService.search(
            List.of(chunk1, chunk2), "query", defaultOptions()
        );

        assertThat(result).hasSize(2);

        SearchService.SearchMatch match1 = result.stream()
            .filter(m -> m.chunk().id().equals("chk-1")).findFirst().orElseThrow();
        assertThat(match1.fusionScore()).isEqualTo(1.0 / (60 + 1));

        SearchService.SearchMatch match2 = result.stream()
            .filter(m -> m.chunk().id().equals("chk-2")).findFirst().orElseThrow();
        assertThat(match2.fusionScore()).isEqualTo(1.0 / (60 + 1));
    }

    @Test
    void shouldFilterByScoreThreshold() {
        when(lexicalIndexService.search(eq("query"), any(), eq(10)))
            .thenReturn(List.of(
                new LexicalIndexService.LexicalHit("chk-1", 0.9, List.of("text")),
                new LexicalIndexService.LexicalHit("chk-2", 0.1, List.of("text"))
            ));
        when(embeddingService.embedQuery("query")).thenReturn(List.of());
        when(vectorIndexService.search(any(), any(), anyInt())).thenReturn(List.of());

        SearchService.SearchOptions options = new SearchService.SearchOptions("lexical", 10, 10, 10, 60, 0.5);
        List<SearchService.SearchMatch> result = searchService.search(List.of(chunk1, chunk2), "query", options);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).chunk().id()).isEqualTo("chk-1");
    }

    @Test
    void shouldLimitByFinalTopK() {
        when(lexicalIndexService.search(eq("query"), any(), eq(10)))
            .thenReturn(List.of(
                new LexicalIndexService.LexicalHit("chk-1", 0.9, List.of("text")),
                new LexicalIndexService.LexicalHit("chk-2", 0.8, List.of("text"))
            ));
        when(embeddingService.embedQuery("query")).thenReturn(List.of());
        when(vectorIndexService.search(any(), any(), anyInt())).thenReturn(List.of());

        SearchService.SearchOptions options = new SearchService.SearchOptions("lexical", 10, 10, 1, 60, null);
        List<SearchService.SearchMatch> result = searchService.search(List.of(chunk1, chunk2), "query", options);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).chunk().id()).isEqualTo("chk-1");
    }

    @Test
    void shouldSkipChunksNotInChunkByIdForLexicalMode() {
        when(lexicalIndexService.search(eq("query"), any(), eq(10)))
            .thenReturn(List.of(
                new LexicalIndexService.LexicalHit("chk-1", 0.9, List.of("text")),
                new LexicalIndexService.LexicalHit("chk-unknown", 0.8, List.of("text"))
            ));
        when(embeddingService.embedQuery("query")).thenReturn(List.of());
        when(vectorIndexService.search(any(), any(), anyInt())).thenReturn(List.of());

        SearchService.SearchOptions options = new SearchService.SearchOptions("lexical", 10, 10, 10, 60, null);
        List<SearchService.SearchMatch> result = searchService.search(List.of(chunk1), "query", options);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).chunk().id()).isEqualTo("chk-1");
    }

    @Test
    void shouldExplainLexicalModeCorrectly() {
        when(embeddingService.embedQuery("query")).thenReturn(List.of());
        when(vectorIndexService.search(any(), any(), anyInt())).thenReturn(List.of());
        when(lexicalIndexService.search(eq("query"), any(), eq(1)))
            .thenReturn(List.of(new LexicalIndexService.LexicalHit("chk-1", 0.75, List.of("text", "keywords"))));

        SearchService.SearchOptions options = new SearchService.SearchOptions("lexical", 10, 10, 10, 60, null);
        SearchService.ExplainResult result = searchService.explain(chunk1, "query", options);

        assertThat(result.fusionScore()).isEqualTo(0.75);
        assertThat(result.lexicalScore()).isEqualTo(0.75);
        assertThat(result.semanticScore()).isEqualTo(0.0);
        assertThat(result.fusionMode()).isEqualTo("lexical");
    }

    @Test
    void shouldExplainSemanticModeCorrectly() {
        when(embeddingService.embedQuery("query")).thenReturn(List.of());
        when(vectorIndexService.search(any(), any(), anyInt()))
            .thenReturn(List.of(new VectorIndexService.SemanticHit("chk-1", 0.88)));
        when(lexicalIndexService.search(eq("query"), any(), eq(1)))
            .thenReturn(List.of(new LexicalIndexService.LexicalHit("chk-1", 0.5, List.of("text"))));

        SearchService.SearchOptions options = new SearchService.SearchOptions("semantic", 10, 10, 10, 60, null);
        SearchService.ExplainResult result = searchService.explain(chunk1, "query", options);

        assertThat(result.fusionScore()).isEqualTo(0.88);
        assertThat(result.semanticScore()).isEqualTo(0.88);
        assertThat(result.fusionMode()).isEqualTo("semantic");
    }

    @Test
    void shouldExplainHybridModeWithRrf() {
        when(embeddingService.embedQuery("query")).thenReturn(List.of());
        when(vectorIndexService.search(any(), any(), anyInt()))
            .thenReturn(List.of(new VectorIndexService.SemanticHit("chk-1", 0.88)));
        when(lexicalIndexService.search(eq("query"), any(), eq(1)))
            .thenReturn(List.of(new LexicalIndexService.LexicalHit("chk-1", 0.75, List.of("text"))));

        SearchService.SearchOptions options = new SearchService.SearchOptions("hybrid", 10, 10, 10, 60, null);
        SearchService.ExplainResult result = searchService.explain(chunk1, "query", options);

        double expectedRrf = 1.0 / (60 + 1) + 1.0 / (60 + 1);
        assertThat(result.fusionScore()).isEqualTo(expectedRrf);
        assertThat(result.fusionMode()).isEqualTo("rrf");
    }
}
