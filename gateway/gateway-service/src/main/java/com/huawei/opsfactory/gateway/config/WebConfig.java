/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration.
 *
 * @author x00000000
 * @since 2026-05-27
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @SuppressWarnings("deprecation")
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // Maintain trailing slash compatibility for existing routes
        // TODO: Migrate to URL pattern-based trailing slash handling when Spring provides replacement
        configurer.setUseTrailingSlashMatch(true);
    }
}
