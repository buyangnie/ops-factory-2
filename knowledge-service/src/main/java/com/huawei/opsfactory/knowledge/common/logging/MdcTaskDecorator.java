/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.knowledge.common.logging;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * The MdcTaskDecorator.
 * @author x00000000
 * @since 2026-05-26
 */

public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                if (contextMap == null || contextMap.isEmpty()) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(contextMap);
                }
                runnable.run();
            } finally {
                if (previous == null || previous.isEmpty()) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(previous);
                }
            }
        };
    }
}
