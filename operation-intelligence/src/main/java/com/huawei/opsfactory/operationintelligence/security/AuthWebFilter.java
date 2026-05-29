/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.security;

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
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Auth Web Filter.
 *
 * @author x00000000
 * @since 2026-05-11
 */
@Component("operationIntelligenceAuthWebFilter")
@Order(1)
public class AuthWebFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(AuthWebFilter.class);

    private static final String HEADER_SECRET_KEY = "x-secret-key";

    private static final String QUERY_KEY = "key";

    private static final String HEALTH_PATH = "/actuator/health";

    private final OperationIntelligenceProperties properties;

    /**
     * Auth Web Filter.
     *
     * @param properties the properties
     */
    public AuthWebFilter(OperationIntelligenceProperties properties) {
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
     * doFilter.
     *
     * @param request the HTTP request
     * @param response the HTTP response
     * @param chain chain
     * @throws IOException ServletException
     * @throws ServletException ServletException
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String path = httpRequest.getRequestURI();
        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod()) || HEALTH_PATH.equals(path)) {
            chain.doFilter(request, response);
            return;
        }

        String key = httpRequest.getHeader(HEADER_SECRET_KEY);
        if (key == null || key.isBlank()) {
            key = httpRequest.getParameter(QUERY_KEY);
        }

        if (!constantTimeEquals(properties.getSecretKey(), key)) {
            log.warn("Rejecting unauthorized request reason=invalid-secret-key");
            httpResponse.sendError(HttpStatus.UNAUTHORIZED.value());
            return;
        }

        chain.doFilter(request, response);
    }
}
