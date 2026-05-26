/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.knowledge.common.model;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, int pageSize, long total) {
}
