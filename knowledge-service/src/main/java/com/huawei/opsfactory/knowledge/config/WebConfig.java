/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.knowledge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * The WebConfig.
 * @author x00000000
 * @since 2026-05-26
 */

@Configuration
public class WebConfig {

    private final KnowledgeProperties properties;

    public WebConfig(KnowledgeProperties properties) {
        this.properties = properties;
    }

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        String corsOrigin = properties.getCorsOrigin();
        if (StringUtils.hasText(corsOrigin)) {
            String[] origins = StringUtils.commaDelimitedListToStringArray(corsOrigin);
            config.setAllowedOriginPatterns(Arrays.asList(origins));
        } else {
            config.setAllowedOriginPatterns(List.of("*"));
        }

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
