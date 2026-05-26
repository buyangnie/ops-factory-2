/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The KnowledgeLoggingProperties.
 * @author x00000000
 * @since 2026-05-26
 */

@ConfigurationProperties(prefix = "knowledge.logging")
public class KnowledgeLoggingProperties {

    private boolean includeQueryText = false;

    public boolean isIncludeQueryText() {
        return includeQueryText;
    }

    public void setIncludeQueryText(boolean includeQueryText) {
        this.includeQueryText = includeQueryText;
    }
}
