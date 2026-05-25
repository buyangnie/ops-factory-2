package com.huawei.opsfactory.businessintelligence.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

@SpringBootTest
class ConfigYamlLoggingPropertiesTest {

    @Autowired
    private Environment environment;

    @Autowired
    private BusinessIntelligenceRuntimeProperties properties;

    @Test
    void shouldLoadLoggingSettingsFromConfigYaml() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        Logger appLogger = context.getLogger("com.huawei.opsfactory.businessintelligence");

        assertThat(properties.getLogging().isAccessLogEnabled()).isTrue();
        assertThat(properties.getBaseDir()).isEqualTo("./data");
        assertThat(properties.isCacheEnabled()).isTrue();
        assertThat(environment.getProperty("business-intelligence.logging.access-log-enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("logging.level.root")).isEqualTo("INFO");
        assertThat(environment.getProperty("logging.level.com.huawei.opsfactory.businessintelligence")).isEqualTo("INFO");
        assertThat(environment.getProperty("logging.level.org.springframework")).isEqualTo("WARN");

        assertThat(rootLogger.getEffectiveLevel()).isEqualTo(Level.INFO);
        assertThat(appLogger.getEffectiveLevel()).isEqualTo(Level.INFO);
    }
}