/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.knowledge;

import com.huawei.opsfactory.knowledge.config.KnowledgeProperties;
import com.huawei.opsfactory.knowledge.config.KnowledgeDatabaseProperties;
import com.huawei.opsfactory.knowledge.config.KnowledgeLoggingProperties;
import com.huawei.opsfactory.knowledge.config.KnowledgeRuntimeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * The KnowledgeServiceApplication.
 * @author x00000000
 * @since 2026-05-26
 */

@SpringBootApplication
@EnableConfigurationProperties({
    KnowledgeProperties.class,
    KnowledgeRuntimeProperties.class,
    KnowledgeDatabaseProperties.class,
    KnowledgeLoggingProperties.class
})
public class KnowledgeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeServiceApplication.class, args);
    }
}
