/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.knowledge.support;

import java.util.ArrayList;
import java.util.List;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/**
 * The TestLogAppender.
 *
 * @author x00000000
 * @since 2026-05-26
 */
public final class TestLogAppender extends AppenderBase<ILoggingEvent> implements AutoCloseable {

    private final ch.qos.logback.classic.Logger logger;
    private final List<ILoggingEvent> events = new ArrayList<>();

    private TestLogAppender(ch.qos.logback.classic.Logger logger) {
        this.logger = logger;
        setName("test-appender-" + logger.getName() + "-" + System.nanoTime());
        start();
        logger.addAppender(this);
    }

    public static TestLogAppender attachTo(Class<?> type) {
        return attachTo(type.getName());
    }

    public static TestLogAppender attachTo(String loggerName) {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(loggerName);
        return new TestLogAppender(logger);
    }

    @Override
    protected void append(ILoggingEvent event) {
        events.add(event);
    }

    public List<ILoggingEvent> events() {
        return List.copyOf(events);
    }

    public List<String> formattedMessages() {
        return events.stream()
            .map(event -> event.getFormattedMessage())
            .toList();
    }

    @Override
    public void close() {
        logger.detachAppender(this);
        stop();
    }
}