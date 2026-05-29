/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.filter.CorsFilter;

/**
 * Web Config Test.
 *
 * @author x00000000
 * @since 2026-05-11
 */
@SpringBootTest
class WebConfigTest {

    @Test
    void corsFilterBeanExists() {
        CorsFilter filter = new WebConfig(new OperationIntelligenceProperties()).corsFilter();
        assertNotNull(filter);
    }

    @Test
    void corsFilterIsServletFilter() {
        CorsFilter filter = new WebConfig(new OperationIntelligenceProperties()).corsFilter();
        assertTrue(filter instanceof jakarta.servlet.Filter);
    }
}