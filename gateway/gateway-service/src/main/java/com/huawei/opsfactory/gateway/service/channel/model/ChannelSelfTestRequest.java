/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service.channel.model;

/**
 * Request body for a channel self-test, containing the text message to send.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public record ChannelSelfTestRequest(String text) {
}
