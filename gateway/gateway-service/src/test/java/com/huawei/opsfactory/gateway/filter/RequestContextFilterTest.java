/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.huawei.opsfactory.gateway.config.GatewayProperties;

import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Test coverage for Request Context Filter.
 *
 * @author x00000000
 * @since 2026-05-26
 */
public class RequestContextFilterTest {
    private RequestContextFilter filter;

    /**
     * Initializes the test fixture before each test method.
     * Creates filter instance with default gateway properties.
     */
    @Before
    public void setUp() {
        GatewayProperties properties = new GatewayProperties();
        filter = new RequestContextFilter(properties);
    }

    /**
     * Tests that request ID is generated when not present in request header.
     * Verifies generated ID is set as request attribute and response header.
     *
     * @throws Exception if filter chain processing fails
     */
    @Test
    public void testGeneratesRequestIdWhenMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/gateway/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        String requestId = (String) request.getAttribute(RequestContextFilter.REQUEST_ID_ATTR);
        assertNotNull("Request ID should be generated", requestId);
        assertEquals(requestId, response.getHeader(RequestContextFilter.REQUEST_ID_HEADER));
    }

    /**
     * Tests that existing request ID in header is reused.
     * Verifies request attribute and response header match the incoming ID.
     *
     * @throws Exception if filter chain processing fails
     */
    @Test
    public void testReusesIncomingRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/gateway/status");
        request.addHeader(RequestContextFilter.REQUEST_ID_HEADER, "req-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals("req-123", request.getAttribute(RequestContextFilter.REQUEST_ID_ATTR));
        assertEquals("req-123", response.getHeader(RequestContextFilter.REQUEST_ID_HEADER));
    }
}