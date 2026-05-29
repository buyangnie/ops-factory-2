/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.Collections;

/**
 * Web Config.
 *
 * @author x00000000
 * @since 2026-05-11
 */
@Configuration("operationIntelligenceWebConfig")
public class WebConfig {

    private final OperationIntelligenceProperties properties;

    /**
     * Web Config.
     *
     * @param properties the properties
     */
    public WebConfig(OperationIntelligenceProperties properties) {
        this.properties = properties;
    }

    /**
     * cors Web Filter.
     *
     * @return the result
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        String corsOrigin = properties.getCorsOrigin();
        if (StringUtils.hasText(corsOrigin)) {
            String[] origins = StringUtils.commaDelimitedListToStringArray(corsOrigin);
            config.setAllowedOriginPatterns(Arrays.asList(origins));
            config.setAllowedHeaders(Arrays.asList("Content-Type", "Authorization", "X-Secret-Key", "X-User-Id"));
            config.setAllowCredentials(true);
        } else {
            config.setAllowedOriginPatterns(Collections.emptyList());
            config.setAllowedHeaders(Collections.emptyList());
            config.setAllowCredentials(false);
        }
        config.setAllowedMethods(Arrays.asList(HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PUT.name(),
            HttpMethod.PATCH.name(), HttpMethod.DELETE.name(), HttpMethod.OPTIONS.name()));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
