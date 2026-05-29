/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.common.model;

import java.util.List;

/**
 * Response model for import operations.
 *
 * @since 2026-05-28
 */
public class ImportResult {

    private int success;
    private int failed;
    private List<ImportError> errors;

    public ImportResult() {
    }

    public ImportResult(int success, int failed, List<ImportError> errors) {
        this.success = success;
        this.failed = failed;
        this.errors = errors;
    }

    public int getSuccess() {
        return success;
    }

    public void setSuccess(int success) {
        this.success = success;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    public List<ImportError> getErrors() {
        return errors;
    }

    public void setErrors(List<ImportError> errors) {
        this.errors = errors;
    }
}
