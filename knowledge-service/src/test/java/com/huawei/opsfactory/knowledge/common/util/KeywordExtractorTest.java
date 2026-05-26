/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.knowledge.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class KeywordExtractorTest {

    @Test
    void shouldReturnEmptyListWhenTextIsNull() {
        assertThat(KeywordExtractor.extract(null, 10)).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenTextIsBlank() {
        assertThat(KeywordExtractor.extract("", 10)).isEmpty();
        assertThat(KeywordExtractor.extract("   ", 10)).isEmpty();
    }

    @Test
    void shouldExtractKeywordsFromEnglishText() {
        List<String> keywords = KeywordExtractor.extract(
            "Machine learning algorithms process training data to build predictive models", 5
        );

        assertThat(keywords).isNotEmpty();
    }

    @Test
    void shouldExtractKeywordsFromChineseText() {
        List<String> keywords = KeywordExtractor.extract(
            "知识库管理系统提供了文档检索和智能问答功能，支持多种文件格式", 5
        );

        assertThat(keywords).isNotEmpty();
    }

    @Test
    void shouldFilterOutEnglishStopWords() {
        List<String> keywords = KeywordExtractor.extract(
            "the system has very good algorithms and can do many things", 20
        );

        assertThat(keywords).doesNotContain("the", "and", "very", "can", "has");
    }

    @Test
    void shouldFilterOutChineseStopWords() {
        List<String> keywords = KeywordExtractor.extract(
            "这个系统可以提供检索功能，但是需要优化", 20
        );

        assertThat(keywords).doesNotContain("这个", "可以", "提供", "但是", "需要");
    }

    @Test
    void shouldRespectMaxKeywordsLimit() {
        List<String> keywords = KeywordExtractor.extract(
            "machine learning deep learning neural network algorithm data processing training model prediction",
            3
        );

        assertThat(keywords).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    void shouldReturnKeywordsSortedByFrequency() {
        List<String> keywords = KeywordExtractor.extract(
            "服务器 服务器 服务器 监控 监控 告警", 10
        );

        assertThat(keywords).hasSizeGreaterThanOrEqualTo(2);
        assertThat(keywords.get(0)).isEqualTo("服务器");
        assertThat(keywords.get(1)).isEqualTo("监控");
    }
}
