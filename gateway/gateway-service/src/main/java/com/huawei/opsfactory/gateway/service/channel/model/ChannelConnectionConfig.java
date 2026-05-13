/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service.channel.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Connection configuration for a channel, including login status, auth directory, and platform-specific identifiers.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChannelConnectionConfig(String loginStatus, String authStateDir, String lastConnectedAt,
    String lastDisconnectedAt, String lastError, String selfPhone, String wechatId, String displayName) {
}
