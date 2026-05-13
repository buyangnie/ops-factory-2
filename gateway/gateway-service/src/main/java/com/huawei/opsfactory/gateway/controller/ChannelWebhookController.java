/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.service.channel.ChannelAdapter;
import com.huawei.opsfactory.gateway.service.channel.ChannelAdapterRegistry;

import reactor.core.publisher.Mono;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.util.Map;

/**
 * Public endpoint for receiving and verifying external channel webhook callbacks.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RestController
@RequestMapping("/gateway/channels/webhooks")
public class ChannelWebhookController {
    private static final Logger log = LoggerFactory.getLogger(ChannelWebhookController.class);

    private final ChannelAdapterRegistry channelAdapterRegistry;

    /**
     * Creates the channel webhook controller instance.
     *
     * @author x00000000
     * @since 2026-05-09
     */
    public ChannelWebhookController(ChannelAdapterRegistry channelAdapterRegistry) {
        this.channelAdapterRegistry = channelAdapterRegistry;
    }

    /**
     * Verifies a WhatsApp webhook challenge request.
     *
     * @param channelId the channelId parameter
     * @param exchange the exchange parameter
     * @return the result
     */
    @GetMapping(value = "/whatsapp/{channelId}", produces = MediaType.TEXT_PLAIN_VALUE)
    public Mono<ResponseEntity<String>> verifyWhatsAppWebhook(@PathVariable("channelId") String channelId,
        ServerWebExchange exchange) {
        ChannelAdapter adapter = channelAdapterRegistry.require("whatsapp");
        return adapter.verifyWebhook(channelId, exchange).map(ResponseEntity::ok);
    }

    /**
     * Receives and processes an incoming WhatsApp webhook event.
     *
     * @author x00000000
     * @since 2026-05-09
     */
    @PostMapping(value = "/whatsapp/{channelId}", consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> receiveWhatsAppWebhook(@PathVariable("channelId") String channelId,
        @RequestBody String body, ServerWebExchange exchange) {
        ChannelAdapter adapter = channelAdapterRegistry.require("whatsapp");
        return adapter.handleWebhook(channelId, body, exchange)
            .thenReturn(ResponseEntity.ok(Map.<String, Object> of("status", "ok")));
    }
}
