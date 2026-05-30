/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.e2e;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.huawei.opsfactory.gateway.common.model.ManagedInstance;

import reactor.core.publisher.Mono;

import org.junit.Test;
import org.springframework.http.HttpMethod;

/**
 * E2E tests for PromptController endpoints.
 *
 * @author x00000000
 * @since 2026-05-30
 */
public class PromptEndpointE2ETest extends BaseE2ETest {
    /**
     * Verifies prompt list requests are proxied to goosed.
     */
    @Test
    public void listPrompts_authenticated_proxiesToGoosed() {
        ManagedInstance instance = new ManagedInstance("test-agent", "alice", 9000, 123L, null, "test-secret");
        instance.setStatus(ManagedInstance.Status.RUNNING);
        when(instanceManager.getOrSpawn("test-agent", "alice")).thenReturn(Mono.just(instance));
        when(goosedProxy.fetchJson(eq(9000), eq(HttpMethod.GET), eq("/config/prompts"), isNull(), eq(30),
            eq("test-secret"))).thenReturn(Mono.just("{\"prompts\":[]}"));

        webClient.get()
            .uri("/gateway/agents/test-agent/config/prompts")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "alice")
            .exchange()
            .expectStatus()
            .isOk();

        verify(goosedProxy).fetchJson(eq(9000), eq(HttpMethod.GET), eq("/config/prompts"), isNull(), eq(30),
            eq("test-secret"));
    }

    /**
     * Verifies unauthenticated prompt requests return 401.
     */
    @Test
    public void listPrompts_unauthenticated_returns401() {
        webClient.get().uri("/gateway/agents/test-agent/config/prompts").exchange().expectStatus().isUnauthorized();
    }
}
