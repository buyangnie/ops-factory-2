/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.knowledge.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsonsTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldSerializeObjectToJson() {
        String json = Jsons.write(objectMapper, List.of("a", "b", "c"));
        assertThat(json).isEqualTo("[\"a\",\"b\",\"c\"]");
    }

    @Test
    void shouldDeserializeStringList() {
        List<String> result = Jsons.readStringList(objectMapper, "[\"x\",\"y\"]");
        assertThat(result).containsExactly("x", "y");
    }

    @Test
    void shouldReturnEmptyListForNullInput() {
        assertThat(Jsons.readStringList(objectMapper, null)).isEmpty();
    }

    @Test
    void shouldReturnEmptyListForBlankInput() {
        assertThat(Jsons.readStringList(objectMapper, "")).isEmpty();
        assertThat(Jsons.readStringList(objectMapper, "   ")).isEmpty();
    }

    @Test
    void shouldDeserializeMap() {
        Map<String, Object> result = Jsons.readMap(objectMapper, "{\"key\":\"value\",\"num\":42}");
        assertThat(result).containsEntry("key", "value");
        assertThat(result).containsEntry("num", 42);
    }

    @Test
    void shouldReturnEmptyMapForNullInput() {
        assertThat(Jsons.readMap(objectMapper, null)).isEmpty();
    }

    @Test
    void shouldReturnEmptyMapForBlankInput() {
        assertThat(Jsons.readMap(objectMapper, "")).isEmpty();
        assertThat(Jsons.readMap(objectMapper, "   ")).isEmpty();
    }

    @Test
    void shouldThrowOnInvalidJsonForReadStringList() {
        assertThatThrownBy(() -> Jsons.readStringList(objectMapper, "not json"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to deserialize json list");
    }

    @Test
    void shouldThrowOnInvalidJsonForReadMap() {
        assertThatThrownBy(() -> Jsons.readMap(objectMapper, "not json"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to deserialize json map");
    }

    @Test
    void shouldSerializeMapToJson() {
        String json = Jsons.write(objectMapper, Map.of("name", "test"));
        assertThat(json).contains("\"name\"");
        assertThat(json).contains("\"test\"");
    }
}
