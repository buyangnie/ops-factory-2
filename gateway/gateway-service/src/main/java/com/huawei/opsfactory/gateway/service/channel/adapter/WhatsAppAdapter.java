/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service.channel.adapter;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import com.huawei.opsfactory.gateway.service.channel.ChannelAdapter;
import com.huawei.opsfactory.gateway.service.channel.ChannelConfigService;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelConnectionConfig;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelConnectivityResult;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelDetail;

import reactor.core.publisher.Mono;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import java.util.Locale;

/**
 * {@link ChannelAdapter} implementation for WhatsApp Web channels, providing connectivity testing based on login state.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@Service
public class WhatsAppAdapter implements ChannelAdapter {
    private final ChannelConfigService channelConfigService;

    /**
     * Creates the whats app adapter instance.
     *
     * @author x00000000
     * @since 2026-05-09
     */
    public WhatsAppAdapter(ChannelConfigService channelConfigService) {
        this.channelConfigService = channelConfigService;
    }

    /**
     * Returns the WhatsApp channel type identifier.
     *
     * @return the result
     */
    @Override
    public String type() {
        return "whatsapp";
    }

    /**
     * Rejects webhook verification requests since WhatsApp Web channels do not use webhooks.
     *
     * @param channelId the channelId parameter
     * @param exchange the exchange parameter
     * @return the result
     */
    @Override
    public Mono<String> verifyWebhook(String channelId, ServerWebExchange exchange) {
        channelConfigService.recordEvent(channelId, "warning", "webhook.unsupported",
            "WhatsApp Web mode does not use webhook verification");
        return Mono.error(new ResponseStatusException(BAD_REQUEST, "WhatsApp Web mode does not use webhooks"));
    }

    /**
     * Rejects webhook handling requests since WhatsApp Web channels do not use webhooks.
     *
     * @param channelId the channelId parameter
     * @param rawBody the rawBody parameter
     * @param exchange the exchange parameter
     * @return the result
     */
    @Override
    public Mono<Void> handleWebhook(String channelId, String rawBody, ServerWebExchange exchange) {
        channelConfigService.recordEvent(channelId, "warning", "webhook.unsupported",
            "WhatsApp Web mode received an unexpected webhook call");
        return Mono.error(new ResponseStatusException(BAD_REQUEST, "WhatsApp Web mode does not use webhooks"));
    }

    /**
     * Tests the connectivity of a WhatsApp channel based on its current login status.
     *
     * @param channelId the channelId parameter
     * @param ownerUserId the ownerUserId parameter
     * @return the result
     */
    @Override
    public Mono<ChannelConnectivityResult> testConnectivity(String channelId, String ownerUserId) {
        ChannelDetail channel = requireChannel(channelId, ownerUserId);
        ChannelConnectionConfig config = channel.config();
        String status = config.loginStatus() == null || config.loginStatus().isBlank() ? "disconnected"
            : config.loginStatus().trim().toLowerCase(Locale.ROOT);

        return switch (status) {
            case "connected": {
                channelConfigService.recordEvent(channelId, ownerUserId, "info", "whatsapp.status",
                    "WhatsApp Web session is connected");
                yield Mono.just(new ChannelConnectivityResult(true, "WhatsApp Web session connected"));
            }
            case "pending":
                yield Mono.just(new ChannelConnectivityResult(false, "WhatsApp Web login is pending"));
            case "error":
                yield Mono.just(
                    new ChannelConnectivityResult(false, config.lastError() == null || config.lastError().isBlank()
                        ? "WhatsApp Web connection error" : config.lastError()));
            default:
                yield Mono.just(new ChannelConnectivityResult(false, "WhatsApp Web login required"));
        };
    }

    private ChannelDetail requireChannel(String channelId, String ownerUserId) {
        ChannelDetail detail = channelConfigService.getChannel(channelId, ownerUserId);
        if (detail == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Channel not found");
        }
        if (!"whatsapp".equals(detail.type())) {
            throw new ResponseStatusException(BAD_REQUEST, "Channel is not a WhatsApp channel");
        }
        return detail;
    }
}
