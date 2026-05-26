/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package db.migration.common;

import java.sql.Connection;
import org.flywaydb.core.api.migration.Context;

/**
 * The V2__add_source_runtime_columns.
 * @author x00000000
 * @since 2026-05-26
 */

public class V2__add_source_runtime_columns extends BaseMetadataMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        executeIfMissing(connection, "knowledge_source", "runtime_status",
            "alter table knowledge_source add column runtime_status TEXT NOT NULL DEFAULT 'ACTIVE'");
        executeIfMissing(connection, "knowledge_source", "runtime_message",
            "alter table knowledge_source add column runtime_message TEXT");
        executeIfMissing(connection, "knowledge_source", "current_job_id",
            "alter table knowledge_source add column current_job_id TEXT");
        executeIfMissing(connection, "knowledge_source", "last_job_error",
            "alter table knowledge_source add column last_job_error TEXT");
        executeIfMissing(connection, "knowledge_source", "rebuild_required",
            "alter table knowledge_source add column rebuild_required INTEGER NOT NULL DEFAULT 0");
    }
}
