/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.knowledge.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdsTest {

    @Test
    void shouldStartWithPrefix() {
        String id = Ids.newId("src");
        assertThat(id).startsWith("src_");
    }

    @Test
    void shouldContainHexSuffix() {
        String id = Ids.newId("chk");
        String suffix = id.substring("chk_".length());
        assertThat(suffix).hasSize(12);
        assertThat(suffix).matches("[0-9a-f]{12}");
    }

    @Test
    void shouldGenerateUniqueIds() {
        String id1 = Ids.newId("doc");
        String id2 = Ids.newId("doc");
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void shouldHandleEmptyPrefix() {
        String id = Ids.newId("");
        assertThat(id).startsWith("_");
        assertThat(id.length()).isGreaterThan(1);
    }
}
