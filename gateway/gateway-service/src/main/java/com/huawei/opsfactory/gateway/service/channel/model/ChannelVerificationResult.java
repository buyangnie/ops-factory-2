/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service.channel.model;

import java.util.List;

/**
 * Result of channel configuration verification, listing any issues found during validation.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public record ChannelVerificationResult(boolean ok, List<String> issues) {
}
