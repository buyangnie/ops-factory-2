/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.knowledge.common.error;

/**
 * The ApiConflictException.
 * @author x00000000
 * @since 2026-05-26
 */

public class ApiConflictException extends RuntimeException {

    private final String code;

    public ApiConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
