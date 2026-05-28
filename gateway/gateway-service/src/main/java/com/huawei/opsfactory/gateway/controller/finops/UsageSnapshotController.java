/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller.finops;

import com.huawei.opsfactory.gateway.model.finops.UsageSnapshotModels.SnapshotPayload;
import com.huawei.opsfactory.gateway.service.finops.UsageSnapshotService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint for gateway-owned session usage snapshots.
 *
 * @since 2026-05-28
 */
@RestController
@RequestMapping("/gateway/usage")
public class UsageSnapshotController {

    private final UsageSnapshotService snapshotService;

    /**
     * Creates the usage snapshot controller.
     *
     * @param snapshotService usage snapshot service
     */
    public UsageSnapshotController(UsageSnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    /**
     * Returns normalized token usage extracted from gateway-managed goosed sessions.
     *
     * @return current usage snapshot
     */
    @GetMapping(value = "/session-snapshot", produces = MediaType.APPLICATION_JSON_VALUE)
    public SnapshotPayload getSessionSnapshot() {
        return snapshotService.snapshot();
    }
}
