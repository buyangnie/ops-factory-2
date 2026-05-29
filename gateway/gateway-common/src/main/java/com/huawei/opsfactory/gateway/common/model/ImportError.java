/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.common.model;

import java.util.Map;

/**
 * Model representing an import error.
 *
 * @since 2026-05-28
 */
public class ImportError {

    private int row;
    private String code;
    private Map<String, String> params;

    public ImportError() {
    }

    public ImportError(int row, String code, Map<String, String> params) {
        this.row = row;
        this.code = code;
        this.params = params;
    }

    public int getRow() {
        return row;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public void setParams(Map<String, String> params) {
        this.params = params;
    }
}
