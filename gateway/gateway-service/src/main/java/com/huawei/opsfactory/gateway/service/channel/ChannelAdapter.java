/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service.channel;

import jakarta.servlet.http.HttpServletRequest;

import reactor.core.publisher.Mono;

import org.springframework.web.server.ServerWebExchange;

/**
 * Contract for channel-type adapters that handle webhook verification, inbound message processing, and connectivity
 * checks.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public interface ChannelAdapter {

    /**
     * Returns the channel type identifier this adapter handles.
     */
    String type();

    /**
     * Verifies an incoming webhook request for the given channel.
     */
    Mono<String> verifyWebhook(String channelId, ServerWebExchange exchange);

    /**
     * Verifies an incoming webhook request for the given channel (Servlet API).
     */
    String verifyWebhookServlet(String channelId, HttpServletRequest request);

    /**
     * Handles an incoming webhook payload for the given channel.
     */
    Mono<Void> handleWebhook(String channelId, String rawBody, ServerWebExchange exchange);

    /**
     * Handles an incoming webhook payload for the given channel (Servlet API).
     */
    String handleWebhookServlet(String channelId, String rawBody, HttpServletRequest request);

    /**
     * Tests the connectivity of the given channel.
     */
    Mono<com.huawei.opsfactory.gateway.service.channel.model.ChannelConnectivityResult>
        testConnectivity(String channelId, String ownerUserId);
}
