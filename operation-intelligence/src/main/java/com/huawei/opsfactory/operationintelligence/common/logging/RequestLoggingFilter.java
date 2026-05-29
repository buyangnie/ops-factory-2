/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.common.logging;

import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
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
 * Request Logging Filter.
 *
 * @author x00000000
 * @since 2026-05-11
 */
@Component("operationIntelligenceRequestLoggingFilter")
@Order(2)
public class RequestLoggingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private final OperationIntelligenceProperties properties;

    /**
     * Request Logging Filter.
     *
     * @param properties the properties
     */
    public RequestLoggingFilter(OperationIntelligenceProperties properties) {
        this.properties = properties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String requestId = resolveRequestId(httpRequest);
        httpResponse.setHeader(LoggingKeys.REQUEST_ID_HEADER, requestId);

        long startedAt = System.currentTimeMillis();
        MDC.put(LoggingKeys.REQUEST_ID, requestId);
        try {
            chain.doFilter(request, response);
            if (properties.getLogging().isAccessLogEnabled()) {
                log.info("HTTP {} {} completed status={} durationMs={}", httpRequest.getMethod(),
                    httpRequest.getRequestURI(), httpResponse.getStatus(), System.currentTimeMillis() - startedAt);
            }
        } finally {
            MDC.remove(LoggingKeys.REQUEST_ID);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(LoggingKeys.REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return requestId.trim();
    }
}
