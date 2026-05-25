/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.huawei.opsfactory.gateway.filter.RequestContextFilter;
import com.huawei.opsfactory.gateway.support.TestLogAppender;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.config.import=optional:file:src/test/resources/config/test-gateway-config.yaml",
        "logging.level.root=WARN",
        "logging.level.com.huawei.opsfactory.gateway=DEBUG"
    })

/**
 * Test coverage for Gateway Config Import.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class GatewayConfigImportTest {
    @Autowired
    private GatewayProperties properties;

    @Autowired
    private Environment environment;

    /**
     * Verifies that import gateway config yaml into spring environment.
     */
    @Test
    public void shouldImportGatewayConfigYamlIntoSpringEnvironment() {
        assertEquals("127.0.0.1", environment.getProperty("server.address"));
        assertEquals("39001", environment.getProperty("server.port"));
        assertEquals("WARN", environment.getProperty("logging.level.root"));
        assertEquals("DEBUG", environment.getProperty("logging.level.com.huawei.opsfactory.gateway"));

        assertEquals("imported-test-key", properties.getSecretKey());
        assertEquals("https://example.test", properties.getCorsOrigin());
        assertFalse(properties.isGooseTls());
        assertEquals(21, properties.getIdle().getTimeoutMinutes());
        assertEquals(61000L, properties.getIdle().getCheckIntervalMs());
        assertFalse(properties.getLogging().isAccessLogEnabled());
        assertTrue(properties.getLogging().isIncludeUpstreamErrorBody());
        assertTrue(properties.getLogging().isIncludeSseChunkPreview());
        assertEquals(42, properties.getLogging().getSseChunkPreviewMaxChars());
    }

    /**
     * Verifies that apply logging levels to logback runtime.
     */
    @Test
    public void shouldApplyLoggingLevelsToLogbackRuntime() {
        LoggerContext context = (LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        Logger gatewayLogger = context.getLogger("com.huawei.opsfactory.gateway");

        assertEquals(Level.WARN, rootLogger.getEffectiveLevel());
        assertEquals(Level.DEBUG, gatewayLogger.getEffectiveLevel());

        try (TestLogAppender appender = TestLogAppender.attachTo(RequestContextFilter.class)) {
            org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(RequestContextFilter.class);
            logger.debug("request-context-debug-test");
            logger.info("request-context-info-test");

            assertTrue(appender.formattedMessages().contains("request-context-debug-test"));
            assertTrue(appender.formattedMessages().contains("request-context-info-test"));
        }
    }
}