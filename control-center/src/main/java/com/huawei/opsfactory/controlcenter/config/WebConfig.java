/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.controlcenter.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;

@Configuration("controlCenterWebConfig")
/**
 * Web Config.
 *
 * @author x00000000
 * @since 2026-05-27
 */
public class WebConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    private final ControlCenterProperties properties;

    public WebConfig(ControlCenterProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/control-center/**")
                .allowedOrigins(resolveAllowedOrigins())
                .allowedMethods("GET", "POST", "PUT", "OPTIONS", "DELETE")
                .allowedHeaders("content-type", "x-secret-key");
    }

    private String[] resolveAllowedOrigins() {
        Set<String> origins = new LinkedHashSet<>();
        String configuredOrigin = properties.getCorsOrigin();
        if (configuredOrigin == null || configuredOrigin.isBlank()) {
            return new String[0];
        }
        origins.add(configuredOrigin);
        try {
            URI uri = URI.create(configuredOrigin);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();
            if ("127.0.0.1".equals(host) || "localhost".equals(host)) {
                origins.add(buildOrigin(scheme, "127.0.0.1", port));
                origins.add(buildOrigin(scheme, "localhost", port));
            }
        } catch (IllegalArgumentException e) {
            log.warn("Invalid CORS origin format: {}, skipping localhost aliases", configuredOrigin);
        }
        return origins.toArray(String[]::new);
    }

    private static String buildOrigin(String scheme, String host, int port) {
        return port > 0 ? scheme + "://" + host + ":" + port : scheme + "://" + host;
    }
}
