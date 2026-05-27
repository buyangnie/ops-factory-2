/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.e2e;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.huawei.opsfactory.gateway.common.model.ManagedInstance;

import reactor.core.publisher.Mono;

import org.junit.Test;
import org.springframework.http.HttpMethod;

/**
 * E2E tests for CatchAllProxyController:
 * Verifies auth and proxy routing for /agents/{agentId}/status path.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class CatchAllProxyEndpointE2ETest extends BaseE2ETest {

    /**
     * Verifies user access to agent status returns the goosed response.
     */
    @Test
    public void userAccessToStatus_allowed() {
        ManagedInstance instance = new ManagedInstance("test-agent", "alice", 9000, 123L, null, "test-secret");
        instance.setStatus(ManagedInstance.Status.RUNNING);
        when(instanceManager.getOrSpawn("test-agent", "alice")).thenReturn(Mono.just(instance));
        when(goosedProxy.fetchJson(eq(9000), eq(HttpMethod.GET), eq("/status"), isNull(), eq(30), eq("test-secret")))
            .thenReturn(Mono.just("{\"status\":\"ok\"}"));

        webClient.get()
            .uri("/gateway/agents/test-agent/status")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "alice")
            .exchange()
            .expectStatus()
            .isOk();
    }

    /**
     * Verifies unauthenticated requests return 401.
     */
    @Test
    public void unauthenticated_returns401() {
        webClient.get().uri("/gateway/agents/test-agent/status").exchange().expectStatus().isUnauthorized();
    }

    /**
     * Verifies query string is forwarded to goosed instance.
     */
    @Test
    public void queryStringForwarded_toGoosed() {
        ManagedInstance instance = new ManagedInstance("test-agent", "admin", 9000, 123L, null, "test-secret");
        instance.setStatus(ManagedInstance.Status.RUNNING);
        when(instanceManager.getOrSpawn("test-agent", "admin")).thenReturn(Mono.just(instance));
        when(goosedProxy.fetchJson(eq(9000), eq(HttpMethod.GET), eq("/status?verbose=true"), isNull(), eq(30),
            eq("test-secret"))).thenReturn(Mono.just("{\"status\":\"ok\"}"));

        webClient.get()
            .uri("/gateway/agents/test-agent/status?verbose=true")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "admin")
            .exchange()
            .expectStatus()
            .isOk();
    }
}