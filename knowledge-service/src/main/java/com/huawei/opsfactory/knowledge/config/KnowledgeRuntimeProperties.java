/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The KnowledgeRuntimeProperties.
 * @author x00000000
 * @since 2026-05-26
 */

@ConfigurationProperties(prefix = "knowledge.runtime")
public class KnowledgeRuntimeProperties {

    private String baseDir = "./data";

    public String getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }
}
