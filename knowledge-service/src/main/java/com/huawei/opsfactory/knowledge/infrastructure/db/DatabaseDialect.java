/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.knowledge.infrastructure.db;

import java.util.List;

/**
 * The DatabaseDialect.
 * @author x00000000
 * @since 2026-05-26
 */

public interface DatabaseDialect {

    String type();

    String defaultDriverClassName();

    List<String> flywayLocations();
}
