/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service.channel.model;

/**
 * Result of a channel connectivity test, indicating whether the connection is healthy.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public record ChannelConnectivityResult(boolean ok, String message) {
}
