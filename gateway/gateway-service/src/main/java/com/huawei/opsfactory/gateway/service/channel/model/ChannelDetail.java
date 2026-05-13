/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service.channel.model;

import java.util.List;

/**
 * Full detail view of a channel including configuration, bindings, events, and verification status.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public record ChannelDetail(String id, String name, String type, boolean enabled, String defaultAgentId,
    String ownerUserId, String createdAt, String updatedAt, String webhookPath, ChannelConnectionConfig config,
    ChannelVerificationResult verification, List<ChannelBinding> bindings, List<ChannelEvent> events) {
}
