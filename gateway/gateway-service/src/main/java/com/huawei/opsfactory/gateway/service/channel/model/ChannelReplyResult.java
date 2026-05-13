/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service.channel.model;

/**
 * Result of a channel reply operation, capturing the agent response text along with session and conversation context.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public record ChannelReplyResult(String channelId, String accountId, String peerId, String conversationId,
    String threadId, String conversationType, String syntheticUserId, String agentId, String sessionId,
    String replyText) {
}
