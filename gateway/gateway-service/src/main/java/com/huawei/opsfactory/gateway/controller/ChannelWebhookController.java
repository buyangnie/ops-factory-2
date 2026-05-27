/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.service.channel.ChannelAdapterRegistry;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.servicecomb.provider.rest.common.RestSchema;
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

/**
 * Public endpoint for receiving and verifying external channel webhook callbacks.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RestController
@RestSchema(schemaId = "channelWebhookController")
@RequestMapping("/gateway/channels/webhooks")
public class ChannelWebhookController {
    private static final Logger log = LoggerFactory.getLogger(ChannelWebhookController.class);

    private final ChannelAdapterRegistry channelAdapterRegistry;

    /**
     * Creates the channel webhook controller instance.
     *
     * @param channelAdapterRegistry registry of channel adapters keyed by channel type
     */
    public ChannelWebhookController(ChannelAdapterRegistry channelAdapterRegistry) {
        this.channelAdapterRegistry = channelAdapterRegistry;
    }

    /**
     * Verifies a WhatsApp webhook challenge request.
     *
     * @param channelId channel identifier for routing to the correct adapter
     * @param request current HTTP request containing verification parameters
     * @return ResponseEntity with the challenge response string
     */
    @GetMapping(value = "/whatsapp/{channelId}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verifyWhatsAppWebhook(@PathVariable("channelId") String channelId,
        HttpServletRequest request) {
        String result = channelAdapterRegistry.require("whatsapp").verifyWebhookServlet(channelId, request);
        return ResponseEntity.ok(result);
    }

    /**
     * Receives and processes an incoming WhatsApp webhook event.
     *
     * @param channelId channel identifier for routing to the correct adapter
     * @param body raw JSON webhook payload
     * @param request current HTTP request
     * @return ResponseEntity with acknowledgment status
     */
    @PostMapping(value = "/whatsapp/{channelId}", consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> receiveWhatsAppWebhook(@PathVariable("channelId") String channelId,
        @RequestBody String body, HttpServletRequest request) {
        String result = channelAdapterRegistry.require("whatsapp").handleWebhookServlet(channelId, body, request);
        return ResponseEntity.ok(result);
    }
}