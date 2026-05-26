/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.knowledge.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The Jsons.
 * @author x00000000
 * @since 2026-05-26
 */

public final class Jsons {

    private Jsons() {
    }

    public static String write(ObjectMapper objectMapper, Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize json", e);
        }
    }

    public static List<String> readStringList(ObjectMapper objectMapper, String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize json list", e);
        }
    }

    public static Map<String, Object> readMap(ObjectMapper objectMapper, String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize json map", e);
        }
    }
}
