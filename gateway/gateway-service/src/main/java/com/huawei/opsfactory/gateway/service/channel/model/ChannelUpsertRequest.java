/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service.channel.model;

/**
 * Request body for creating or updating a channel configuration.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public record ChannelUpsertRequest(String id, String name, String type, Boolean enabled, String defaultAgentId,
    ChannelConnectionConfig config) {
}
