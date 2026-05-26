/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.knowledge.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenEstimatorTest {

    @Test
    void shouldReturnZeroForNull() {
        assertThat(TokenEstimator.estimate(null)).isEqualTo(0);
    }

    @Test
    void shouldReturnZeroForBlank() {
        assertThat(TokenEstimator.estimate("")).isEqualTo(0);
        assertThat(TokenEstimator.estimate("   ")).isEqualTo(0);
    }

    @Test
    void shouldEstimateEnglishTextByWhitespaceSplit() {
        assertThat(TokenEstimator.estimate("hello world foo")).isEqualTo(3);
    }

    @Test
    void shouldEstimateCjkTextByCharacterCount() {
        int result = TokenEstimator.estimate("知识库管理");
        int expectedCjk = 4 / 2;
        assertThat(result).isEqualTo(expectedCjk);
    }

    @Test
    void shouldReturnMaxOfWordAndCjkEstimate() {
        int result = TokenEstimator.estimate("hello 知识库 world");
        int whitespaceWords = 3;
        int cjkEstimate = 3 / 2;
        assertThat(result).isEqualTo(Math.max(whitespaceWords, cjkEstimate));
    }

    @Test
    void shouldHandlePureWhitespace() {
        assertThat(TokenEstimator.estimate("   ")).isEqualTo(0);
    }

    @Test
    void shouldEstimateSingleWord() {
        assertThat(TokenEstimator.estimate("hello")).isEqualTo(1);
    }

    @Test
    void shouldEstimateSingleCjkCharacter() {
        int result = TokenEstimator.estimate("知");
        int cjk = 1 / 2;
        int whitespace = 1;
        assertThat(result).isEqualTo(Math.max(whitespace, cjk));
    }
}
