/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.config;

import io.micrometer.context.ContextRegistry;

import jakarta.annotation.PostConstruct;

import reactor.core.publisher.Hooks;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures Reactor context propagation to MDC for WebFlux logging.
 * <p>
 * This uses Micrometer Context Propagation to automatically propagate
 * requestId/userId/sessionId from Reactor Context to SLF4J MDC across
 * thread switches in reactive chains.
 * <p>
 * MDC propagation can be disabled via the {@code gateway.logging.mdcPropagationEnabled}
 * configuration property.
 *
 * @author x00000000
 * @since 2026-05-25
 */
@Configuration
public class ReactorMdcConfiguration {

    public static final String REQUEST_ID_KEY = "REQUEST_ID";

    public static final String USER_ID_KEY = "USER_ID";

    public static final String SESSION_ID_KEY = "SESSION_ID";

    private final GatewayProperties properties;

    /**
     * Creates the Reactor MDC configuration instance.
     *
     * @param properties gateway configuration properties
     */
    public ReactorMdcConfiguration(GatewayProperties properties) {
        this.properties = properties;
    }

    /**
     * Initializes Micrometer Context Propagation for Reactor.
     * Called automatically by Spring on application startup.
     */
    @PostConstruct
    public void initializeContextPropagation() {
        if (!properties.getLogging().isMdcPropagationEnabled()) {
            return;
        }
        ContextRegistry registry = ContextRegistry.getInstance();
        registry.registerThreadLocalAccessor("requestId", () -> MDC.get("requestId"),
            value -> MDC.put("requestId", value), () -> MDC.remove("requestId"));
        registry.registerThreadLocalAccessor("userId", () -> MDC.get("userId"), value -> MDC.put("userId", value),
            () -> MDC.remove("userId"));
        registry.registerThreadLocalAccessor("sessionId", () -> MDC.get("sessionId"),
            value -> MDC.put("sessionId", value), () -> MDC.remove("sessionId"));

        Hooks.enableAutomaticContextPropagation();
    }

    /**
     * Marker class to ensure initialization happens once.
     * <p>
     * This bean is created after the {@link #initializeContextPropagation()} method completes,
     * guaranteeing that Reactor hooks are registered before the application serves requests.
     *
     * @return the initializer instance
     */
    @Bean
    public ReactorMdcInitializer reactorMdcInitializer() {
        return new ReactorMdcInitializer();
    }

    /**
     * Marker class that ensures Reactor MDC hooks are initialized once.
     * <p>
     * The {@code @PostConstruct} method {@link #initializeContextPropagation()} registers the
     * Micrometer context propagation hooks. This bean guarantees Spring completes that initialization
     * before accepting traffic.
     */
    public static final class ReactorMdcInitializer {
    }
}