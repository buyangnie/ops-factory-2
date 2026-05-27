/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.huawei.opsfactory.gateway.common.model.ManagedInstance;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.process.InstanceManager;
import com.huawei.opsfactory.gateway.proxy.GoosedProxy;

import reactor.core.publisher.Mono;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Test coverage for Catch All Proxy Controller.
 *
 * @author x00000000
 * @since 2026-05-26
 */
public class CatchAllProxyControllerTest {
    private static final String TEST_AGENT_ID = "test-agent";

    private static final String TEST_USER_ID = "alice";

    private static final String ADMIN_USER_ID = "admin";

    private static final String SECRET_KEY = "test-secret";

    private static final int INSTANCE_PORT = 9000;

    private static final int TIMEOUT_SECONDS = 30;

    private InstanceManager instanceManager;

    private GoosedProxy goosedProxy;

    private CatchAllProxyController controller;

    /**
     * Initializes the test fixture before each test method.
     * Sets up mocked instance manager and goosed proxy.
     */
    @Before
    public void setUp() {
        instanceManager = mock(InstanceManager.class);
        goosedProxy = mock(GoosedProxy.class);
        controller = new CatchAllProxyController(instanceManager, goosedProxy);
    }

    /**
     * Tests that authenticated user can access agent status endpoint.
     * Verifies request is proxied to the correct instance port.
     */
    @Test
    public void testAuthenticatedAccess_status() {
        MockHttpServletRequest request =
            new MockHttpServletRequest("GET", "/gateway/agents/" + TEST_AGENT_ID + "/status");
        request.setAttribute(UserContextFilter.USER_ID_ATTR, TEST_USER_ID);

        ManagedInstance instance =
            new ManagedInstance(TEST_AGENT_ID, TEST_USER_ID, INSTANCE_PORT, 123L, null, SECRET_KEY);
        when(instanceManager.getOrSpawn(TEST_AGENT_ID, TEST_USER_ID)).thenReturn(Mono.just(instance));
        when(goosedProxy.fetchJson(eq(INSTANCE_PORT), any(), eq("/status"), any(), eq(TIMEOUT_SECONDS), eq(SECRET_KEY)))
            .thenReturn(Mono.just("{\"status\":\"ok\"}"));

        String result = controller.proxyStatus(TEST_AGENT_ID, request);

        assertEquals("{\"status\":\"ok\"}", result);
        verify(instanceManager).getOrSpawn(TEST_AGENT_ID, TEST_USER_ID);
    }

    /**
     * Tests that user can access agent system_info endpoint.
     * Verifies request is proxied correctly to goosed instance.
     */
    @Test
    public void testUserAccessToSystemInfo_allowed() {
        MockHttpServletRequest request =
            new MockHttpServletRequest("GET", "/gateway/agents/" + TEST_AGENT_ID + "/system_info");
        request.setAttribute(UserContextFilter.USER_ID_ATTR, TEST_USER_ID);

        ManagedInstance instance =
            new ManagedInstance(TEST_AGENT_ID, TEST_USER_ID, INSTANCE_PORT, 123L, null, SECRET_KEY);
        when(instanceManager.getOrSpawn(TEST_AGENT_ID, TEST_USER_ID)).thenReturn(Mono.just(instance));
        when(goosedProxy.fetchJson(eq(INSTANCE_PORT), any(), eq("/system_info"), any(), eq(TIMEOUT_SECONDS),
            eq(SECRET_KEY))).thenReturn(Mono.just("{\"info\":\"test\"}"));

        ResponseEntity<String> result = controller.proxySystemInfo(TEST_AGENT_ID, request);

        assertEquals("{\"info\":\"test\"}", result.getBody());
        assertEquals(MediaType.APPLICATION_JSON, result.getHeaders().getContentType());
        verify(instanceManager).getOrSpawn(TEST_AGENT_ID, TEST_USER_ID);
    }

    /**
     * Tests that query string is forwarded to goosed instance.
     * Verifies query parameters are preserved in proxied request.
     */
    @Test
    public void testQueryStringForwarding() {
        String queryString = "limit=5";
        MockHttpServletRequest request =
            new MockHttpServletRequest("GET", "/gateway/agents/" + TEST_AGENT_ID + "/status?" + queryString);
        request.setQueryString(queryString);
        request.setAttribute(UserContextFilter.USER_ID_ATTR, ADMIN_USER_ID);

        ManagedInstance instance =
            new ManagedInstance(TEST_AGENT_ID, ADMIN_USER_ID, INSTANCE_PORT, 123L, null, SECRET_KEY);
        when(instanceManager.getOrSpawn(TEST_AGENT_ID, ADMIN_USER_ID)).thenReturn(Mono.just(instance));
        when(goosedProxy.fetchJson(eq(INSTANCE_PORT), any(), eq("/status?" + queryString), any(), eq(TIMEOUT_SECONDS),
            eq(SECRET_KEY))).thenReturn(Mono.just("{\"status\":\"ok\"}"));

        controller.proxyStatus(TEST_AGENT_ID, request);

        verify(goosedProxy).fetchJson(eq(INSTANCE_PORT), any(), eq("/status?" + queryString), any(),
            eq(TIMEOUT_SECONDS), eq(SECRET_KEY));
    }

    /**
     * Tests that instance manager exceptions are propagated.
     * Verifies RuntimeException is thrown when instance manager fails.
     */
    @Test
    public void testInstanceManagerThrowsException() {
        MockHttpServletRequest request =
            new MockHttpServletRequest("GET", "/gateway/agents/" + TEST_AGENT_ID + "/status");
        request.setAttribute(UserContextFilter.USER_ID_ATTR, ADMIN_USER_ID);

        when(instanceManager.getOrSpawn(TEST_AGENT_ID, ADMIN_USER_ID))
            .thenThrow(new RuntimeException("Instance not found"));

        try {
            controller.proxyStatus(TEST_AGENT_ID, request);
            fail("Expected RuntimeException");
        } catch (RuntimeException ex) {
            assertEquals("Instance not found", ex.getMessage());
        }
    }
}
