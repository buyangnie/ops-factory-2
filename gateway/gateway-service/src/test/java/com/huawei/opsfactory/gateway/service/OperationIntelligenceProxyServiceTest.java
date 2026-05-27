/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.huawei.opsfactory.gateway.config.GatewayProperties;

import reactor.core.publisher.Mono;

import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeoutException;

/**
 * Test coverage for operation intelligence proxy service.
 *
 * @author x00000000
 * @since 2026-05-20
 */
public class OperationIntelligenceProxyServiceTest {
    /**
     * Tests gateway path prefix is removed before forwarding.
     */
    @Test
    public void testTargetPathStripsGatewayPrefix() {
        MockHttpServletRequest request =
            new MockHttpServletRequest("GET", "/gateway/operation-intelligence/graph/resources/tree");
        OperationIntelligenceProxyService service = new OperationIntelligenceProxyService(new GatewayProperties());

        assertEquals("/operation-intelligence/graph/resources/tree", service.targetPath(request));
    }

    /**
     * Tests operation intelligence service path is preserved when called without gateway prefix.
     */
    @Test
    public void testTargetPathPreservesBackendPath() {
        MockHttpServletRequest request =
            new MockHttpServletRequest("GET", "/operation-intelligence/graph/resources/tree");
        OperationIntelligenceProxyService service = new OperationIntelligenceProxyService(new GatewayProperties());

        assertEquals("/operation-intelligence/graph/resources/tree", service.targetPath(request));
    }

    /**
     * Tests operation intelligence proxy has a response buffer above Spring WebClient default.
     */
    @Test
    public void testOperationIntelligenceProxyResponseLimitSupportsLargeGraphExports() {
        GatewayProperties properties = new GatewayProperties();

        assertTrue(properties.getOperationIntelligence().getMaxResponseSizeMb() >= 10);
        new OperationIntelligenceProxyService(properties);
    }

    /**
     * Tests upstream timeout is converted to 504 instead of bubbling as 500.
     */
    @Test
    public void testProxyMapsTimeoutToGatewayTimeout() {
        OperationIntelligenceProxyService service =
            createServiceWithError(Mono.error(new TimeoutException("slow upstream")));
        MockHttpServletRequest request = proxyRequest();

        try {
            service.proxy(request);
            fail("Expected ResponseStatusException");
        } catch (ResponseStatusException ex) {
            assertEquals(HttpStatus.GATEWAY_TIMEOUT, ex.getStatusCode());
        }
    }

    /**
     * Tests upstream connection failures are converted to 503.
     */
    @Test
    public void testProxyMapsConnectionFailureToServiceUnavailable() {
        WebClientRequestException connectionError =
            new WebClientRequestException(new IOException("connection refused"), HttpMethod.GET,
                URI.create("http://127.0.0.1:8096/operation-intelligence/graph/resources/tree"), HttpHeaders.EMPTY);
        OperationIntelligenceProxyService service = createServiceWithError(Mono.error(connectionError));
        MockHttpServletRequest request = proxyRequest();

        try {
            service.proxy(request);
            fail("Expected ResponseStatusException");
        } catch (ResponseStatusException ex) {
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        }
    }

    private OperationIntelligenceProxyService
        createServiceWithError(Mono<org.springframework.web.reactive.function.client.ClientResponse> error) {
        GatewayProperties properties = new GatewayProperties();
        properties.getOperationIntelligence().setBaseUrl("http://127.0.0.1:8096");
        WebClient webClient = WebClient.builder().exchangeFunction(request -> error).build();
        return new OperationIntelligenceProxyService(properties, webClient);
    }

    private MockHttpServletRequest proxyRequest() {
        MockHttpServletRequest request =
            new MockHttpServletRequest("GET", "/gateway/operation-intelligence/graph/resources/tree");
        request.setContentType("application/json");
        return request;
    }
}
