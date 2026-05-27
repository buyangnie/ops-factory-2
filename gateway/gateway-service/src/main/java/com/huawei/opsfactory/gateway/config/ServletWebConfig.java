/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Servlet configuration that registers the CORS filter and related web-layer beans.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@Configuration
public class ServletWebConfig {
    private final GatewayProperties properties;

    /**
     * Creates the servlet web config instance.
     */
    public ServletWebConfig(GatewayProperties properties) {
        this.properties = properties;
    }

    /**
     * Creates and configures the CORS filter bean.
     *
     * @return the CORS filter
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public Filter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        String configured = properties.getCorsOrigin();

        if (configured == null || configured.isBlank() || "*".equals(configured.trim())) {
            config.addAllowedOriginPattern("*");
        } else {
            List<String> allowedOrigins =
                Arrays.stream(configured.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
            config.setAllowedOrigins(allowedOrigins);
        }

        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(
            Arrays.asList("x-secret-key", "x-user-id", "x-request-id", "content-type", "authorization"));
        config.setExposedHeaders(Arrays.asList("*"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source) {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain filterChain) throws ServletException, IOException {
                String requestOrigin = request.getHeader(HttpHeaders.ORIGIN);
                String allowOrigin = resolveAllowOrigin(configured, requestOrigin);

                if (allowOrigin != null) {
                    response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, allowOrigin);
                    response.setHeader(HttpHeaders.VARY, HttpHeaders.ORIGIN);
                }

                response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, PUT, DELETE, OPTIONS");
                response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                    "x-secret-key, x-user-id, x-request-id, content-type, authorization");
                response.setHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "*");
                response.setHeader(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600");

                if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                    if (requestOrigin != null && allowOrigin == null) {
                        response.setStatus(HttpStatus.FORBIDDEN.value());
                        return;
                    }
                    response.setStatus(HttpStatus.NO_CONTENT.value());
                    return;
                }

                filterChain.doFilter(request, response);
            }
        };
    }

    private String resolveAllowOrigin(String configured, String requestOrigin) {
        if (requestOrigin == null || requestOrigin.isBlank()) {
            return null;
        }
        if (configured == null || configured.isBlank() || "*".equals(configured.trim())) {
            return requestOrigin;
        }

        List<String> exactOrigins =
            Arrays.stream(configured.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return exactOrigins.contains(requestOrigin) ? requestOrigin : null;
    }
}