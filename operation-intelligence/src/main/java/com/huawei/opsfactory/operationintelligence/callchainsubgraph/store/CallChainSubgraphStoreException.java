/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.callchainsubgraph.store;

/**
 * Unchecked exception for call chain subgraph persistence failures.
 *
 * @author x00000000
 * @since 2026-05-29
 */
public class CallChainSubgraphStoreException extends RuntimeException {
    /**
     * Constructs an exception with message and cause.
     *
     * @param message the failure message
     * @param cause the root cause
     */
    public CallChainSubgraphStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
