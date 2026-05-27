/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.huawei.opsfactory.gateway.process.PrewarmService;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Test coverage for User Context Filter.
 *
 * @author x00000000
 * @since 2026-05-26
 */
public class UserContextFilterTest {
    private static final String USER_ID = "user123";

    private static final String ADMIN_USER_ID = "admin";

    private static final String SYSTEM_ENDPOINT = "/gateway/status";

    private static final String TEST_ENDPOINT = "/test";

    private static final String EMPTY_USER_ID = "";

    private static final String TRACE_START_PATH = "/gateway/agents/qa-agent/sessions/20260429_3/trace";

    private static final String TRACE_DOWNLOAD_PATH = "/gateway/session-traces/job-1/download";

    private static final String AGENTS_ENDPOINT = "/gateway/agents";

    private UserContextFilter filter;

    private PrewarmService prewarmService;

    /**
     * Initializes the test fixture before each test method.
     * Creates filter instance with mocked prewarm service.
     */
    @Before
    public void setUp() {
        prewarmService = mock(PrewarmService.class);
        filter = new UserContextFilter(prewarmService);
    }

    /**
     * Tests that user ID is extracted from request header.
     * Verifies user ID is set as request attribute.
     *
     * @throws Exception if filter chain processing fails
     */
    @Test
    public void testExtractsUserIdFromHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", TEST_ENDPOINT);
        request.addHeader("x-user-id", USER_ID);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(USER_ID, request.getAttribute(UserContextFilter.USER_ID_ATTR));
    }

    /**
     * Tests that request is rejected when user ID header is missing.
     * Verifies 400 status is returned and user ID attribute is not set.
     *
     * @throws Exception if filter chain processing fails
     */
    @Test
    public void testRejects400WhenNoUserIdHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", TEST_ENDPOINT);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatus());
        assertNull(request.getAttribute(UserContextFilter.USER_ID_ATTR));
    }

    /**
     * Tests that system endpoints pass through without user context.
     * Verifies system status endpoint does not require user ID.
     *
     * @throws Exception if filter chain processing fails
     */
    @Test
    public void testSystemEndpointPassesThroughWithoutUserContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", SYSTEM_ENDPOINT);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNull(request.getAttribute(UserContextFilter.USER_ID_ATTR));
    }

    /**
     * Tests that empty user ID returns 400 status.
     * Verifies blank user ID is rejected.
     *
     * @throws Exception if filter chain processing fails
     */
    @Test
    public void testEmptyUserIdReturns400() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", TEST_ENDPOINT);
        request.addHeader("x-user-id", EMPTY_USER_ID);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatus());
        assertNull(request.getAttribute(UserContextFilter.USER_ID_ATTR));
    }

    /**
     * Tests that trace start endpoint does not trigger prewarm.
     * Verifies user prewarm is skipped for trace operations.
     *
     * @throws Exception if filter chain processing fails
     */
    @Test
    public void testTraceStartDoesNotPrewarmUser() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", TRACE_START_PATH);
        request.addHeader("x-user-id", ADMIN_USER_ID);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(ADMIN_USER_ID, request.getAttribute(UserContextFilter.USER_ID_ATTR));
        verify(prewarmService, never()).onUserActivity(ADMIN_USER_ID);
    }

    /**
     * Tests that trace download endpoint does not trigger prewarm.
     * Verifies user prewarm is skipped for trace download operations.
     *
     * @throws Exception if filter chain processing fails
     */
    @Test
    public void testTraceDownloadDoesNotPrewarmUser() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", TRACE_DOWNLOAD_PATH);
        request.addHeader("x-user-id", ADMIN_USER_ID);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(ADMIN_USER_ID, request.getAttribute(UserContextFilter.USER_ID_ATTR));
        verify(prewarmService, never()).onUserActivity(ADMIN_USER_ID);
    }

    /**
     * Tests that regular gateway requests trigger user prewarm.
     * Verifies prewarm service is called for normal user requests.
     *
     * @throws Exception if filter chain processing fails
     */
    @Test
    public void testRegularGatewayRequestPrewarmsUser() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", AGENTS_ENDPOINT);
        request.addHeader("x-user-id", ADMIN_USER_ID);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        verify(prewarmService).onUserActivity(ADMIN_USER_ID);
    }
}