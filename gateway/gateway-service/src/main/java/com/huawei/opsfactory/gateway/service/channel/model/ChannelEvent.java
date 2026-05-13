/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service.channel.model;

/**
 * An audit event recorded for a channel, such as creation, login, or error occurrences.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public record ChannelEvent(String id, String channelId, String level, String type, String summary, String createdAt) {
}
