/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.filter;

import static org.junit.Assert.assertEquals;

import com.huawei.opsfactory.gateway.config.GatewayProperties;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Test coverage for Auth Web Filter.
 *
 * @author x00000000
 * @since 2026-05-26
 */
public class AuthWebFilterTest {
    private static final String STATUS_ENDPOINT = "/status";

    private static final String AGENTS_ENDPOINT = "/agents";

    private static final String SECRET_KEY = "test-secret";

    private static final String WRONG_KEY = "wrong-key";

    private static final String HEADER_SECRET_KEY = "x-secret-key";

    private static final String QUERY_PARAM_KEY = "key";

    private AuthWebFilter filter;

    private GatewayProperties properties;

    /**
     * Initializes the test fixture before each test method.
     * Creates filter instance with test secret key.
     */
    @Before
    public void setUp() {
        properties = new GatewayProperties();
        properties.setSecretKey(SECRET_KEY);
        filter = new AuthWebFilter(properties);
    }

    /**
     * Tests that status endpoint is public and accessible.
     * Verifies status endpoint requires no authentication.
     *
     * @throws Exception if filter chain processing fails
     */
    @Test
    public void testStatusEndpointIsPublic() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", STATUS_ENDPOINT);
        request.addHeader(HEADER_SECRET_KEY, SECRET_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(HttpStatus.OK.value(), response.getStatus());
    }

    /**
     * Tests that OPTIONS preflight requests pass through.
     * Verifies CORS preflight is allowed without authentication.
     *
     * @throws Exception if filter chain processing fails
     */
    @Test
    public void testOptionsPassesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", AGENTS_ENDPOINT);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(HttpStatus.OK.value(), response.getStatus());
    }

    /**
     * Tests that valid secret key in header is accepted.
     * Verifies authentication succeeds with correct header.
     *
     * @throws Exception if filter chain processing fails
     */
    @Test
    public void testValidSecretKeyInHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", AGENTS_ENDPOINT);
        request.addHeader(HEADER_SECRET_KEY, SECRET_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(HttpStatus.OK.value(), response.getStatus());
    }

    /**
     * Tests that valid secret key in query parameter is accepted.
     * Verifies authentication succeeds with correct query param.
     *
     * @throws Exception if filter chain processing fails
     */
    @Test
    public void testValidSecretKeyInQueryParam() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", AGENTS_ENDPOINT);
        request.addParameter(QUERY_PARAM_KEY, SECRET_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(HttpStatus.OK.value(), response.getStatus());
    }

    /**
     * Tests that invalid secret key returns 401 status.
     * Verifies authentication fails with incorrect header.
     *
     * @throws Exception if filter chain processing fails
     */
    @Test
    public void testInvalidSecretKeyReturns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", AGENTS_ENDPOINT);
        request.addHeader(HEADER_SECRET_KEY, WRONG_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
    }

    /**
     * Tests that missing secret key returns 401 status.
     * Verifies authentication fails when no credentials provided.
     *
     * @throws Exception if filter chain processing fails
     */
    @Test
    public void testMissingSecretKeyReturns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", AGENTS_ENDPOINT);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
    }
}