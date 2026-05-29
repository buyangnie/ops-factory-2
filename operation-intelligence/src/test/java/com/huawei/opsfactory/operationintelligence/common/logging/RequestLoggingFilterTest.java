/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.common.logging;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;

import org.junit.jupiter.api.Test;

/**
 * Request Logging Filter Test.
 *
 * @author x00000000
 * @since 2026-05-18
 */
class RequestLoggingFilterTest {

    @Test
    void testConstructor() {
        OperationIntelligenceProperties properties = new OperationIntelligenceProperties();
        RequestLoggingFilter filter = new RequestLoggingFilter(properties);

        assertNotNull(filter);
    }

    @Test
    void testFilterExists() {
        OperationIntelligenceProperties properties = new OperationIntelligenceProperties();
        RequestLoggingFilter filter = new RequestLoggingFilter(properties);

        // Filter is a Servlet Filter
        assertTrue(filter instanceof jakarta.servlet.Filter);
    }
}