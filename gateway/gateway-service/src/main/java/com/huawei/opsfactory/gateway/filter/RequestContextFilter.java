/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.filter;

import com.huawei.opsfactory.gateway.config.GatewayProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that assigns a unique request ID and logs access details for each HTTP request.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@Component("gatewayRequestContextFilter")
@Order(1)
public class RequestContextFilter implements jakarta.servlet.Filter {
    public static final String REQUEST_ID_ATTR = "requestId";

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static final Logger log = LoggerFactory.getLogger(RequestContextFilter.class);

    private final GatewayProperties properties;

    /**
     * Creates the request context filter instance.
     *
     * @param properties gateway configuration properties
     */
    public RequestContextFilter(GatewayProperties properties) {
        this.properties = properties;
    }

    /**
     * Assigns a unique request ID and logs access details for each HTTP request.
     *
     * @param servletRequest the servlet request
     * @param servletResponse the servlet response
     * @param filterChain the filter chain
     * @throws IOException if an I/O error occurs
     * @throws ServletException if a servlet error occurs
     */
    @Override
    public void doFilter(jakarta.servlet.ServletRequest servletRequest, jakarta.servlet.ServletResponse servletResponse,
        FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String requestId = resolveRequestId(request);
        long startedAt = System.currentTimeMillis();

        request.setAttribute(REQUEST_ID_ATTR, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        MDC.put("requestId", requestId);

        try {
            filterChain.doFilter(request, response);

            if (properties.getLogging().isAccessLogEnabled()) {
                int status = response.getStatus();
                String userId = (String) request.getAttribute(UserContextFilter.USER_ID_ATTR);
                log.info("HTTP {} {} completed status={} durationMs={}", request.getMethod(), request.getRequestURI(),
                    status, System.currentTimeMillis() - startedAt);
            }
        } finally {
            MDC.remove("requestId");
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return requestId.trim();
    }
}