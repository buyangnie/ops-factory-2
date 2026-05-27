/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.filter;

import com.huawei.opsfactory.gateway.common.constants.GatewayConstants;
import com.huawei.opsfactory.gateway.process.PrewarmService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Servlet filter that resolves the authenticated user identity from request headers.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@Component
@Order(3)
public class UserContextFilter implements jakarta.servlet.Filter {
    private static final Logger log = LoggerFactory.getLogger(UserContextFilter.class);

    private static final String CHANNEL_WEBHOOK_PREFIX = "/gateway/channels/webhooks/";

    public static final String USER_ID_ATTR = "userId";

    private final PrewarmService prewarmService;

    /**
     * Creates the user context filter instance.
     *
     * @param prewarmService service for pre-warming agent instances
     */
    public UserContextFilter(PrewarmService prewarmService) {
        this.prewarmService = prewarmService;
    }

    private static boolean isSystemEndpoint(String path) {
        return path.equals("/status") || path.equals("/me") || path.equals("/config") || path.equals("/gateway/status")
            || path.equals("/gateway/me") || path.equals("/gateway/config");
    }

    private static boolean isTraceEndpoint(String path) {
        if (path == null) {
            return false;
        }
        if (path.startsWith("/gateway/session-traces/")) {
            return true;
        }
        if (!path.startsWith("/gateway/agents/") || !path.endsWith("/trace")) {
            return false;
        }
        return path.substring("/gateway/agents/".length()).contains("/sessions/");
    }

    /**
     * Resolves the authenticated user identity from request headers.
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

        String path = request.getRequestURI();

        if (path.startsWith(CHANNEL_WEBHOOK_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        // CORS preflight passes through without user context
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String userId = request.getHeader(GatewayConstants.HEADER_USER_ID);
        if (userId == null || userId.isBlank()) {
            userId = request.getParameter(GatewayConstants.QUERY_UID);
        }

        if (userId == null || userId.isBlank()) {
            // System endpoints don't require user context
            if (isSystemEndpoint(path)) {
                filterChain.doFilter(request, response);
                return;
            }
            log.warn("Rejecting request path={} reason=missing-user-id", path);
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return;
        }

        request.setAttribute(USER_ID_ATTR, userId);
        MDC.put("userId", userId);

        // Trigger pre-warm for authenticated users, except diagnostics that must not mutate runtime state.
        if (!isTraceEndpoint(path)) {
            prewarmService.onUserActivity(userId);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("userId");
        }
    }
}