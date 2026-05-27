/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.support;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test appender for capturing log events during assertions.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public final class TestLogAppender extends AppenderBase<ILoggingEvent> implements AutoCloseable {
    private final ch.qos.logback.classic.Logger logger;

    private final List<ILoggingEvent> events = new CopyOnWriteArrayList<>();

    private TestLogAppender(ch.qos.logback.classic.Logger logger) {
        this.logger = logger;
        setName("test-appender-" + logger.getName() + "-" + System.nanoTime());
        start();
        logger.addAppender(this);
    }

    /**
     * Executes the attach to operation.
     *
     * @param type the type parameter
     * @return the result
     */
    public static TestLogAppender attachTo(Class<?> type) {
        ch.qos.logback.classic.Logger logger =
            (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(type.getName());
        return new TestLogAppender(logger);
    }

    /**
     * Executes the append operation.
     *
     * @param event the event parameter
     */
    @Override
    protected void append(ILoggingEvent event) {
        events.add(event);
    }

    /**
     * Executes the formatted messages operation.
     *
     * @return the result
     */
    public List<String> formattedMessages() {
        return events.stream().map(event -> event.getFormattedMessage()).toList();
    }

    /**
     * Executes the close operation.
     */
    @Override
    public void close() {
        logger.detachAppender(this);
        stop();
    }
}