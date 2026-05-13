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
 * {@link ChannelAdapter} implementation for WeChat channels, providing connectivity testing based on login state.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@Service
public class WeChatAdapter implements ChannelAdapter {
    private final ChannelConfigService channelConfigService;

    /**
     * Creates the we chat adapter instance.
     *
     * @author x00000000
     * @since 2026-05-09
     */
    public WeChatAdapter(ChannelConfigService channelConfigService) {
        this.channelConfigService = channelConfigService;
    }

    /**
     * Returns the WeChat channel type identifier.
     *
     * @return the result
     */
    @Override
    public String type() {
        return "wechat";
    }

    /**
     * Rejects webhook verification requests since WeChat channels do not use webhooks.
     *
     * @param channelId the channelId parameter
     * @param exchange the exchange parameter
     * @return the result
     */
    @Override
    public Mono<String> verifyWebhook(String channelId, ServerWebExchange exchange) {
        return Mono.error(new ResponseStatusException(BAD_REQUEST, "WeChat channel does not use webhooks"));
    }

    /**
     * Rejects webhook handling requests since WeChat channels do not use webhooks.
     *
     * @param channelId the channelId parameter
     * @param rawBody the rawBody parameter
     * @param exchange the exchange parameter
     * @return the result
     */
    @Override
    public Mono<Void> handleWebhook(String channelId, String rawBody, ServerWebExchange exchange) {
        return Mono.error(new ResponseStatusException(BAD_REQUEST, "WeChat channel does not use webhooks"));
    }

    /**
     * Tests the connectivity of a WeChat channel based on its current login status.
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
                channelConfigService.recordEvent(channelId, ownerUserId, "info", "wechat.status",
                    "WeChat session is connected");
                yield Mono.just(new ChannelConnectivityResult(true, "WeChat session connected"));
            }
            case "pending":
                yield Mono.just(new ChannelConnectivityResult(false, "WeChat QR login is pending"));
            case "error":
                yield Mono.just(
                    new ChannelConnectivityResult(false, config.lastError() == null || config.lastError().isBlank()
                        ? "WeChat connection error" : config.lastError()));
            default:
                yield Mono.just(new ChannelConnectivityResult(false, "WeChat login required"));
        };
    }

    private ChannelDetail requireChannel(String channelId, String ownerUserId) {
        ChannelDetail detail = channelConfigService.getChannel(channelId, ownerUserId);
        if (detail == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Channel not found");
        }
        if (!"wechat".equals(detail.type())) {
            throw new ResponseStatusException(BAD_REQUEST, "Channel is not a WeChat channel");
        }
        return detail;
    }
}
