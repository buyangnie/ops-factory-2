/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service.channel.model;

/**
 * Summary view of a channel for list operations, including status and binding counts.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public record ChannelSummary(String id, String name, String type, boolean enabled, String defaultAgentId,
    String ownerUserId, String status, String lastInboundAt, String lastOutboundAt, int bindingCount) {
}
