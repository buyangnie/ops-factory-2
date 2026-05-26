/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.knowledge.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.huawei.opsfactory.knowledge.service.EmbeddingService;
import com.huawei.opsfactory.knowledge.service.KnowledgeServiceFacade;
import com.huawei.opsfactory.knowledge.support.TestLogAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.core.env.Environment;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = {
    "knowledge.runtime.base-dir=target/test-runtime-config-yaml",
    "knowledge.logging.include-query-text=false",
    "logging.level.root=INFO",
    "logging.level.com.huawei.opsfactory.knowledge=INFO",
    "logging.level.com.huawei.opsfactory.knowledge.service.EmbeddingService=WARN",
    "logging.level.com.huawei.opsfactory.knowledge.service.SearchService=INFO"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConfigYamlLoggingPropertiesTest {

    @Autowired
    private Environment environment;

    @Autowired
    private KnowledgeLoggingProperties knowledgeLoggingProperties;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldLoadLoggingSettingsFromConfigYaml() {
        LoggerContext context = (LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        Logger facadeLogger = context.getLogger(KnowledgeServiceFacade.class.getName());
        Logger embeddingLogger = context.getLogger(EmbeddingService.class.getName());

        assertThat(knowledgeLoggingProperties.isIncludeQueryText()).isFalse();
        assertThat(environment.getProperty("knowledge.logging.include-query-text", Boolean.class)).isFalse();
        assertThat(environment.getProperty("logging.level.root")).isEqualTo("INFO");
        assertThat(environment.getProperty("logging.level.com.huawei.opsfactory.knowledge")).isEqualTo("INFO");
        assertThat(environment.getProperty("logging.level.com.huawei.opsfactory.knowledge.service.EmbeddingService")).isEqualTo("WARN");
        assertThat(environment.getProperty("logging.level.com.huawei.opsfactory.knowledge.service.SearchService")).isEqualTo("INFO");

        assertThat(rootLogger.getEffectiveLevel()).isEqualTo(Level.INFO);
        assertThat(facadeLogger.getEffectiveLevel()).isEqualTo(Level.INFO);
        assertThat(embeddingLogger.getEffectiveLevel()).isEqualTo(Level.WARN);
    }

    @Test
    void shouldSuppressEmbeddingDebugLogsBecauseConfigYamlSetsWarnLevel() {
        try (TestLogAppender appender = TestLogAppender.attachTo(EmbeddingService.class)) {
            embeddingService.embedQuery("config yaml debug suppression");

            assertThat(appender.formattedMessages())
                .noneMatch(message -> message.contains("Using local embeddings because remote embedding is not enabled"));
        }
    }

    @Test
    void shouldAllowFacadeInfoLogsBecauseConfigYamlSetsInfoLevel() throws Exception {
        try (TestLogAppender appender = TestLogAppender.attachTo(KnowledgeServiceFacade.class)) {
            mockMvc.perform(post("/knowledge/sources")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "name": "config-yaml-log-test-source",
                          "description": "config yaml logging verification"
                        }
                        """))
                .andExpect(status().isOk());

            assertThat(appender.formattedMessages())
                .anyMatch(message -> message.contains("Created source sourceId="));
        }
    }
}