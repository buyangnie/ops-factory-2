/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.knowledge.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.opsfactory.knowledge.service.EmbeddingService;
import com.huawei.opsfactory.knowledge.service.KnowledgeServiceFacade;
import com.huawei.opsfactory.knowledge.support.TestLogAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = {
    "knowledge.runtime.base-dir=target/test-runtime-logging-config",
    "logging.level.root=ERROR",
    "logging.level.com.huawei.opsfactory.knowledge=INFO",
    "logging.level.com.huawei.opsfactory.knowledge.service.EmbeddingService=DEBUG"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LoggingConfigurationTest {

    @Autowired
    private EmbeddingService embeddingService;

    @Test
    void shouldLoadLogbackConfigurationAndApplyConfiguredLevels() {
        LoggerContext context = (LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        Logger facadeLogger = context.getLogger(KnowledgeServiceFacade.class.getName());
        Logger embeddingLogger = context.getLogger(EmbeddingService.class.getName());

        assertThat(rootLogger.getAppender("Console")).isNotNull();
        assertThat(rootLogger.getAppender("File")).isNotNull();
        assertThat(rootLogger.getEffectiveLevel()).isEqualTo(Level.ERROR);
        assertThat(facadeLogger.getEffectiveLevel()).isEqualTo(Level.INFO);
        assertThat(embeddingLogger.getEffectiveLevel()).isEqualTo(Level.DEBUG);

        try (
            TestLogAppender embeddingAppender = TestLogAppender.attachTo(EmbeddingService.class);
            TestLogAppender outsideAppender = TestLogAppender.attachTo("outside.test")
        ) {
            embeddingService.embedQuery("ITSM deployment");
            org.slf4j.Logger outsideLogger = org.slf4j.LoggerFactory.getLogger("outside.test");
            outsideLogger.debug("outside debug");
            outsideLogger.error("outside error");

            assertThat(embeddingAppender.formattedMessages())
                .anyMatch(message -> message.contains("Using local embeddings because remote embedding is not enabled"));
            assertThat(outsideAppender.formattedMessages()).containsExactly("outside error");
        }
    }
}