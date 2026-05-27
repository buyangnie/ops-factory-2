/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.huawei.opsfactory.gateway.filter.OperationIntelligenceProxyFilter;
import com.huawei.opsfactory.gateway.service.OperationIntelligenceProxyService;

import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Test coverage for operation intelligence proxy controller.
 *
 * @author x00000000
 * @since 2026-05-20
 */
public class OperationIntelligenceProxyControllerTest {
    /**
     * Tests graph proxy.
     */
    @Test
    public void testGraphProxy() throws Exception {
        OperationIntelligenceProxyService proxyService = Mockito.mock(OperationIntelligenceProxyService.class);
        when(proxyService.proxy(any())).thenReturn(
            ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, "application/json").body("{\"success\":true}"));

        OperationIntelligenceProxyFilter filter = new OperationIntelligenceProxyFilter(proxyService);
        MockHttpServletRequest request =
            new MockHttpServletRequest("POST", "/gateway/operation-intelligence/graph/resources/tree");
        request.setQueryString("envCode=prod");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        assertEquals("{\"success\":true}", response.getContentAsString());
    }

    /**
     * Tests requests outside operation-intelligence path fall through.
     */
    @Test
    public void testNonProxyPathFallsThrough() throws Exception {
        OperationIntelligenceProxyService proxyService = Mockito.mock(OperationIntelligenceProxyService.class);
        OperationIntelligenceProxyFilter filter = new OperationIntelligenceProxyFilter(proxyService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/gateway/status");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        verifyNoInteractions(proxyService);
    }
}
