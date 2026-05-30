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
import org.springframework.http.MediaType;

/**
 * E2E tests for ScheduleController endpoints.
 *
 * @author x00000000
 * @since 2026-05-30
 */
public class ScheduleEndpointE2ETest extends BaseE2ETest {
    /**
     * Verifies schedule list requests are proxied to goosed.
     */
    @Test
    public void listSchedules_authenticated_proxiesToGoosed() {
        ManagedInstance instance = new ManagedInstance("test-agent", "alice", 9000, 123L, null, "test-secret");
        instance.setStatus(ManagedInstance.Status.RUNNING);
        when(instanceManager.getOrSpawn("test-agent", "alice")).thenReturn(Mono.just(instance));
        when(goosedProxy.fetchJson(eq(9000), eq(HttpMethod.GET), eq("/schedule/list"), isNull(), eq(30),
            eq("test-secret"))).thenReturn(Mono.just("{\"jobs\":[]}"));

        webClient.get()
            .uri("/gateway/agents/test-agent/schedule/list")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "alice")
            .exchange()
            .expectStatus()
            .isOk();

        verify(goosedProxy).fetchJson(eq(9000), eq(HttpMethod.GET), eq("/schedule/list"), isNull(), eq(30),
            eq("test-secret"));
    }

    /**
     * Verifies schedule create requests are proxied to goosed.
     */
    @Test
    public void createSchedule_authenticated_proxiesToGoosed() {
        ManagedInstance instance = new ManagedInstance("test-agent", "alice", 9000, 123L, null, "test-secret");
        instance.setStatus(ManagedInstance.Status.RUNNING);
        when(instanceManager.getOrSpawn("test-agent", "alice")).thenReturn(Mono.just(instance));
        when(goosedProxy.fetchJson(eq(9000), eq(HttpMethod.POST), eq("/schedule/create"),
            eq("{\"id\":\"job-1\"}"), eq(30), eq("test-secret"))).thenReturn(Mono.just("{\"id\":\"job-1\"}"));

        webClient.post()
            .uri("/gateway/agents/test-agent/schedule/create")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "alice")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"id\":\"job-1\"}")
            .exchange()
            .expectStatus()
            .isOk();

        verify(goosedProxy).fetchJson(eq(9000), eq(HttpMethod.POST), eq("/schedule/create"), eq("{\"id\":\"job-1\"}"),
            eq(30), eq("test-secret"));
    }

    /**
     * Verifies schedule session list forwards query parameters.
     */
    @Test
    public void listScheduleSessions_forwardsQueryString() {
        ManagedInstance instance = new ManagedInstance("test-agent", "alice", 9000, 123L, null, "test-secret");
        instance.setStatus(ManagedInstance.Status.RUNNING);
        when(instanceManager.getOrSpawn("test-agent", "alice")).thenReturn(Mono.just(instance));
        when(goosedProxy.fetchJson(eq(9000), eq(HttpMethod.GET), eq("/schedule/job-1/sessions?limit=5"), isNull(),
            eq(30), eq("test-secret"))).thenReturn(Mono.just("[]"));

        webClient.get()
            .uri("/gateway/agents/test-agent/schedule/job-1/sessions?limit=5")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "alice")
            .exchange()
            .expectStatus()
            .isOk();

        verify(goosedProxy).fetchJson(eq(9000), eq(HttpMethod.GET), eq("/schedule/job-1/sessions?limit=5"), isNull(),
            eq(30), eq("test-secret"));
    }
}
