/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.common.util;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

/**
 * YAML configuration file loader.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public final class YamlLoader {
    private YamlLoader() {
    }

    /**
     * Loads a YAML file as a map and returns an empty map when the file is absent.
     *
     * @param path the path parameter
     * @return the result
     */
    public static Map<String, Object> load(Path path) {
        if (!Files.exists(path)) {
            return Collections.emptyMap();
        }
        try (InputStream is = Files.newInputStream(path)) {
            Yaml yaml = new Yaml();
            Map<String, Object> result = yaml.load(is);
            return result != null ? result : Collections.emptyMap();
        } catch (YAMLException e) {
            throw new IllegalStateException("Invalid YAML: " + path + ": " + e.getMessage(), e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load YAML: " + path, e);
        }
    }

    /**
     * Returns a string value from a map, or the default when absent.
     *
     * @param map the map parameter
     * @param key the key parameter
     * @param defaultValue the defaultValue parameter
     * @return the result
     */
    public static String getString(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    /**
     * Returns an integer value from a map, or the default when absent or invalid.
     *
     * @param map the map parameter
     * @param key the key parameter
     * @param defaultValue the defaultValue parameter
     * @return the result
     */
    public static int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object val = map.get(key);
        if (val instanceof Number n) {
            return n.intValue();
        }
        if (val instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
