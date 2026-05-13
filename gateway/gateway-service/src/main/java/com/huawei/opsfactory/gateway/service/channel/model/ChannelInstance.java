/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service.channel.model;

/**
 * Persistent channel instance stored in the configuration file system.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public record ChannelInstance(String id, String name, String type, boolean enabled, String defaultAgentId,
    String ownerUserId, String createdAt, String updatedAt, ChannelConnectionConfig config) {
}
