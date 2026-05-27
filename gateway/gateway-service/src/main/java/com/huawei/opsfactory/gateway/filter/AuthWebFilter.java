/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.filter;

import com.huawei.opsfactory.gateway.common.constants.GatewayConstants;
import com.huawei.opsfactory.gateway.config.GatewayProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Servlet filter that validates the secret key on every non-preflight, non-webhook request.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@Component("gatewayAuthWebFilter")
@Order(2)
public class AuthWebFilter implements jakarta.servlet.Filter {
    private static final Logger log = LoggerFactory.getLogger(AuthWebFilter.class);

    private static final String CHANNEL_WEBHOOK_PREFIX = "/gateway/channels/webhooks/";

    private final GatewayProperties properties;

    /**
     * Creates the auth servlet filter instance.
     *
     * @param properties gateway configuration properties containing the secret key
     */
    public AuthWebFilter(GatewayProperties properties) {
        this.properties = properties;
    }

    /**
     * Compares two strings in constant time to prevent timing attacks.
     *
     * @param a first string
     * @param b second string
     * @return true if strings are equal, false otherwise
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] aBytes = digest.digest(a.getBytes(StandardCharsets.UTF_8));
            byte[] bBytes = digest.digest(b.getBytes(StandardCharsets.UTF_8));

            int result = 0;
            for (int i = 0; i < Math.min(aBytes.length, bBytes.length); i++) {
                result |= aBytes[i] ^ bBytes[i];
            }
            return result == 0;
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }

    /**
     * Filters incoming HTTP requests by validating the secret key.
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

        // OPTIONS preflight passes through
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (request.getRequestURI().startsWith(CHANNEL_WEBHOOK_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Check secret key from header or query param
        String key = request.getHeader(GatewayConstants.HEADER_SECRET_KEY);
        if (key == null || key.isBlank()) {
            key = request.getParameter(GatewayConstants.QUERY_KEY);
        }

        if (!constantTimeEquals(properties.getSecretKey(), key)) {
            log.warn("Rejecting unauthorized request path={} reason=invalid-secret-key", request.getRequestURI());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        }

        filterChain.doFilter(request, response);
    }
}