/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.hook;

import reactor.core.publisher.Mono;

/**
 * Contract for request-processing hooks that can inspect or modify the request context.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public interface RequestHook {

    /**
     * Process a request through this hook.
     * May modify the context body or reject the request by returning Mono.error().
     */
    Mono<HookContext> process(HookContext ctx);
}
