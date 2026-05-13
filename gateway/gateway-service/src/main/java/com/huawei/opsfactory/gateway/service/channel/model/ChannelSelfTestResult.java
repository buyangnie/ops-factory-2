/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service.channel.model;

/**
 * Result of a channel self-test, containing the agent reply and associated session information.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public record ChannelSelfTestResult(String channelId, String selfPhone, String agentId, String sessionId,
    String replyText) {
}
