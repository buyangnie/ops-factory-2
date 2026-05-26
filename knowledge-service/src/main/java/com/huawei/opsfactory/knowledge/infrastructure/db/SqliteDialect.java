/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.knowledge.infrastructure.db;

import java.util.List;

/**
 * The SqliteDialect.
 * @author x00000000
 * @since 2026-05-26
 */

public class SqliteDialect implements DatabaseDialect {

    @Override
    public String type() {
        return "sqlite";
    }

    @Override
    public String defaultDriverClassName() {
        return "org.sqlite.JDBC";
    }

    @Override
    public List<String> flywayLocations() {
        return List.of("classpath:db/migration/common");
    }
}
