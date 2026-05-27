/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import jakarta.servlet.FilterChain;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthWebFilterTest {

    private AuthWebFilter filter;

    private FilterChain chain;

    @BeforeEach
    void setUp() {
        OperationIntelligenceProperties props = new OperationIntelligenceProperties();
        props.setSecretKey("test-secret");
        filter = new AuthWebFilter(props);
        chain = mock(FilterChain.class);
    }

    @Test
    void validSecretKeyHeader_passes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        request.addHeader("x-secret-key", "test-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void validSecretKeyQueryParam_passes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        request.setParameter("key", "test-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void missingSecretKey_returns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void invalidSecretKey_returns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        request.addHeader("x-secret-key", "wrong");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void optionsRequest_passesWithoutAuth() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void healthCheck_passesWithoutAuth() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void blankSecretKeyHeader_treatedAsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        request.addHeader("x-secret-key", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
    }
}
