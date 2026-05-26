/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.knowledge.infrastructure.db;

import java.util.List;

/**
 * The PostgresqlDialect.
 * @author x00000000
 * @since 2026-05-26
 */

public class PostgresqlDialect implements DatabaseDialect {

    @Override
    public String type() {
        return "postgresql";
    }

    @Override
    public String defaultDriverClassName() {
        return "org.postgresql.Driver";
    }

    @Override
    public List<String> flywayLocations() {
        return List.of("classpath:db/migration/common");
    }
}
