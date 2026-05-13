/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service.channel.model;

/**
 * Represents the binding between a channel conversation and an internal agent session.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public record ChannelBinding(String channelId, String accountId, String peerId, String conversationId, String threadId,
    String conversationType, String ownerUserId, String syntheticUserId, String agentId, String sessionId,
    String lastInboundAt, String lastOutboundAt) {
}
