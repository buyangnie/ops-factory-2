/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.knowledge.common.util;

import java.util.UUID;

/**
 * The Ids.
 * @author x00000000
 * @since 2026-05-26
 */

public final class Ids {

    private Ids() {
    }

    public static String newId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
