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
 * Test coverage for Recipe Controller.
 *
 * @author x00000000
 * @since 2026-05-30
 */
public class RecipeControllerTest {
    private static final String TEST_AGENT_ID = "test-agent";

    private static final String TEST_USER_ID = "alice";

    private static final String SECRET_KEY = "test-secret";

    private static final int INSTANCE_PORT = 9000;

    private InstanceManager instanceManager;

    private GoosedProxy goosedProxy;

    private RecipeController controller;

    /**
     * Initializes test fixtures.
     */
    @Before
    public void setUp() {
        instanceManager = mock(InstanceManager.class);
        goosedProxy = mock(GoosedProxy.class);
        controller = new RecipeController(instanceManager, goosedProxy);
    }

    /**
     * Tests saving a recipe proxies the request body.
     */
    @Test
    public void saveRecipe_proxiesBody() {
        MockHttpServletRequest request = request("POST", "/gateway/agents/test-agent/recipes/save");
        when(goosedProxy.fetchJson(eq(INSTANCE_PORT), eq(HttpMethod.POST), eq("/recipes/save"),
            eq("{\"recipe\":{\"title\":\"demo\"}}"), eq(30), eq(SECRET_KEY))).thenReturn(Mono.just("{\"id\":\"demo\"}"));

        ResponseEntity<String> result = controller.saveRecipe(TEST_AGENT_ID, "{\"recipe\":{\"title\":\"demo\"}}",
            request);

        assertEquals("{\"id\":\"demo\"}", result.getBody());
        assertEquals(MediaType.APPLICATION_JSON, result.getHeaders().getContentType());
    }

    /**
     * Tests listing recipes proxies to goosed.
     */
    @Test
    public void listRecipes_proxiesToGoosed() {
        MockHttpServletRequest request = request("GET", "/gateway/agents/test-agent/recipes/list");
        when(goosedProxy.fetchJson(eq(INSTANCE_PORT), eq(HttpMethod.GET), eq("/recipes/list"), eq(null), eq(30),
            eq(SECRET_KEY))).thenReturn(Mono.just("{\"manifests\":[]}"));

        ResponseEntity<String> result = controller.listRecipes(TEST_AGENT_ID, request);

        assertEquals("{\"manifests\":[]}", result.getBody());
        verify(goosedProxy).fetchJson(eq(INSTANCE_PORT), eq(HttpMethod.GET), eq("/recipes/list"), eq(null), eq(30),
            eq(SECRET_KEY));
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
