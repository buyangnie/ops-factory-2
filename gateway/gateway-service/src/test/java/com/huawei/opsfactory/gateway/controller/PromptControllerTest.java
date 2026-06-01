/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import static org.junit.Assert.assertEquals;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Test coverage for Prompt Controller.
 *
 * @author x00000000
 * @since 2026-05-30
 */
public class PromptControllerTest {
    private static final String TEST_AGENT_ID = "test-agent";

    private static final String TEST_USER_ID = "alice";

    private static final String SECRET_KEY = "test-secret";

    private static final int INSTANCE_PORT = 9000;

    private InstanceManager instanceManager;

    private GoosedProxy goosedProxy;

    private PromptController controller;

    /**
     * Initializes test fixtures.
     */
    @Before
    public void setUp() {
        instanceManager = mock(InstanceManager.class);
        goosedProxy = mock(GoosedProxy.class);
        controller = new PromptController(instanceManager, goosedProxy);
    }

    /**
     * Tests listing prompts is proxied to goosed.
     */
    @Test
    public void listPrompts_proxiesToGoosed() {
        MockHttpServletRequest request = request("GET", "/gateway/agents/test-agent/config/prompts");
        when(goosedProxy.fetchJson(eq(INSTANCE_PORT), eq(HttpMethod.GET), eq("/config/prompts"), eq(null), eq(30),
            eq(SECRET_KEY))).thenReturn(Mono.just("{\"prompts\":[]}"));

        ResponseEntity<String> result = controller.listPrompts(TEST_AGENT_ID, request);

        assertEquals("{\"prompts\":[]}", result.getBody());
        assertEquals(MediaType.APPLICATION_JSON, result.getHeaders().getContentType());
        verify(instanceManager).getOrSpawn(TEST_AGENT_ID, TEST_USER_ID);
    }

    /**
     * Tests getting an individual prompt preserves the file name in the path.
     */
    @Test
    public void getPrompt_proxiesToGoosed() {
        MockHttpServletRequest request = request("GET", "/gateway/agents/test-agent/config/prompts/system.md");
        when(goosedProxy.fetchJson(eq(INSTANCE_PORT), eq(HttpMethod.GET), eq("/config/prompts/system.md"), eq(null),
            eq(30), eq(SECRET_KEY))).thenReturn(Mono.just("{\"name\":\"system.md\"}"));

        ResponseEntity<String> result = controller.getPrompt(TEST_AGENT_ID, "system.md", request);

        assertEquals("{\"name\":\"system.md\"}", result.getBody());
    }

    /**
     * Tests saving a prompt proxies the body upstream.
     */
    @Test
    public void savePrompt_proxiesRequestBody() {
        MockHttpServletRequest request = request("PUT", "/gateway/agents/test-agent/config/prompts/system.md");
        when(goosedProxy.fetchJson(eq(INSTANCE_PORT), eq(HttpMethod.PUT), eq("/config/prompts/system.md"),
            eq("{\"content\":\"hello\"}"), eq(30), eq(SECRET_KEY))).thenReturn(Mono.just("{}"));

        ResponseEntity<String> result = controller.savePrompt(TEST_AGENT_ID, "system.md", "{\"content\":\"hello\"}",
            request);

        assertEquals("{}", result.getBody());
    }

    /**
     * Tests resetting a prompt proxies delete to goosed.
     */
    @Test
    public void resetPrompt_proxiesDelete() {
        MockHttpServletRequest request = request("DELETE", "/gateway/agents/test-agent/config/prompts/system.md");
        when(goosedProxy.fetchJson(eq(INSTANCE_PORT), eq(HttpMethod.DELETE), eq("/config/prompts/system.md"),
            eq(null), eq(30), eq(SECRET_KEY))).thenReturn(Mono.just("{}"));

        ResponseEntity<String> result = controller.resetPrompt(TEST_AGENT_ID, "system.md", request);

        assertEquals("{}", result.getBody());
        verify(goosedProxy).fetchJson(eq(INSTANCE_PORT), eq(HttpMethod.DELETE), eq("/config/prompts/system.md"),
            eq(null), eq(30), eq(SECRET_KEY));
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setAttribute(UserContextFilter.USER_ID_ATTR, TEST_USER_ID);
        ManagedInstance instance =
            new ManagedInstance(TEST_AGENT_ID, TEST_USER_ID, INSTANCE_PORT, 123L, null, SECRET_KEY);
        when(instanceManager.getOrSpawn(TEST_AGENT_ID, TEST_USER_ID)).thenReturn(Mono.just(instance));
        return request;
    }
}
