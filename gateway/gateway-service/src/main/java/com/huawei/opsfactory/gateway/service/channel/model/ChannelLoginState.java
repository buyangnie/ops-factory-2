/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service.channel.model;

/**
 * Current login state of a channel, including connection status, QR code data, and session metadata.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public record ChannelLoginState(String channelId, String status, String message, String authStateDir, String selfPhone,
    String lastConnectedAt, String lastDisconnectedAt, String lastError, String qrCodeDataUrl) {
}
