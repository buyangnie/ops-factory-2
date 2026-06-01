/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.huawei.opsfactory.gateway.common.model.ManagedInstance;
import com.huawei.opsfactory.gateway.config.GatewayProperties;
import com.huawei.opsfactory.gateway.config.GlobalExceptionHandler;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.hook.HookContext;
import com.huawei.opsfactory.gateway.hook.HookPipeline;
import com.huawei.opsfactory.gateway.process.InstanceManager;
import com.huawei.opsfactory.gateway.proxy.GoosedProxy;
import com.huawei.opsfactory.gateway.service.AgentConfigService;
import com.huawei.opsfactory.gateway.service.FileService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Test coverage for Reply Controller Real Proxy.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class ReplyControllerRealProxyTest {

    /**
     * Executes the session reply real goosed400 returns gateway error envelope operation.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void sessionReply_realGoosed400ReturnsGatewayErrorEnvelope() throws Exception {
        DisposableServer server =
            HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .route(
                    routes -> routes
                        .post("/agent/resume",
                            (request,
                                response) -> response.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                    .sendString(Mono
                                        .just("{\"session\":{\"id\":\"session-123\"}," + "\"extension_results\":[]}")))
                        .post("/sessions/session-123/reply",
                            (request, response) -> response.status(400)
                                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                                .sendString(Mono.just("Session already has an active request. Cancel " + "it first."))))
                .bindNow();

        try {
            InstanceManager instanceManager = mock(InstanceManager.class);
            HookPipeline hookPipeline = mock(HookPipeline.class);
            AgentConfigService agentConfigService = mock(AgentConfigService.class);
            FileService fileService = mock(FileService.class);
            ManagedInstance instance =
                new ManagedInstance("test-agent", "alice", server.port(), 12345L, null, "test-secret");
            instance.setStatus(ManagedInstance.Status.RUNNING);

            when(instanceManager.getOrSpawn("test-agent", "alice")).thenReturn(Mono.just(instance));
            when(hookPipeline.executeRequest(any(HookContext.class)))
                .thenAnswer(inv -> Mono.just(((HookContext) inv.getArgument(0)).getBody()));
            when(agentConfigService.getUserAgentDir("alice", "test-agent")).thenReturn(Path.of("."));
            when(fileService.listCapsuleRelevantFiles(any())).thenReturn(Collections.emptyList());

            GatewayProperties properties = new GatewayProperties();
            properties.setGooseTls(false);
            GoosedProxy goosedProxy = new GoosedProxy(properties);
            ReplyController controller =
                new ReplyController(instanceManager, goosedProxy, hookPipeline, agentConfigService, fileService);
            MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilter((request, response, chain) -> {
                    request.setAttribute(UserContextFilter.USER_ID_ATTR, "alice");
                    chain.doFilter(request, response);
                })
                .build();

            mockMvc.perform(post("/api/gateway/agents/test-agent/sessions/session-123/reply")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"request_id\":\"00000000-0000-0000-0000-000000000001\",\"user_message\":"
                        + "{\"role\":\"user\",\"created\":1776928807,\"content\":[{\"type\":\"text\",\"text\":"
                        + "\"hello\"}],\"metadata\":{\"userVisible\":true,\"agentVisible\":true}}}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                    org.hamcrest.Matchers.containsString("goosed_active_request_conflict"),
                    org.hamcrest.Matchers.containsString("Session already has an active request. Cancel it first."),
                    org.hamcrest.Matchers.containsString("session-123"),
                    org.hamcrest.Matchers.containsString("test-agent"),
                    org.hamcrest.Matchers.containsString("wait"),
                    org.hamcrest.Matchers.containsString("cancel"),
                    org.hamcrest.Matchers.containsString("retry"))));
        } finally {
            server.disposeNow();
        }
    }

    /**
     * Executes the session events real goosed404 returns gateway error envelope operation.
     */
    @Test
    public void sessionEvents_realGoosed404ReturnsGatewayErrorEnvelope() throws Exception {
        DisposableServer server =
            HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .route(
                    routes -> routes
                        .post("/agent/resume",
                            (request,
                                response) -> response.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                    .sendString(Mono
                                        .just("{\"session\":{\"id\":\"session-123\"}," + "\"extension_results\":[]}")))
                        .get("/sessions/session-123/events",
                            (request, response) -> response.status(404)
                                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                                .sendString(Mono.just("session not found"))))
                .bindNow();

        try {
            InstanceManager instanceManager = mock(InstanceManager.class);
            HookPipeline hookPipeline = mock(HookPipeline.class);
            AgentConfigService agentConfigService = mock(AgentConfigService.class);
            FileService fileService = mock(FileService.class);
            ManagedInstance instance =
                new ManagedInstance("test-agent", "alice", server.port(), 12345L, null, "test-secret");
            instance.setStatus(ManagedInstance.Status.RUNNING);

            when(instanceManager.getOrSpawn("test-agent", "alice")).thenReturn(Mono.just(instance));

            GatewayProperties properties = new GatewayProperties();
            properties.setGooseTls(false);
            GoosedProxy goosedProxy = new GoosedProxy(properties);
            ReplyController controller =
                new ReplyController(instanceManager, goosedProxy, hookPipeline, agentConfigService, fileService);
            MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultRequest(get("/").requestAttr(UserContextFilter.USER_ID_ATTR, "alice"))
                .build();

            mockMvc.perform(get("/api/gateway/agents/test-agent/sessions/session-123/events")
                    .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, org.hamcrest.Matchers.containsString(
                    MediaType.TEXT_EVENT_STREAM_VALUE)))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                    org.hamcrest.Matchers.containsString("event: error"),
                    org.hamcrest.Matchers.containsString("\"error\":\"Agent resource not found\""))));
        } finally {
            server.disposeNow();
        }
    }

    /**
     * Executes the session events active requests drained emits output files after original event operation.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void sessionEvents_drainedActiveReqEmitsOutputFilesAfterEvent() throws Exception {
        DisposableServer server = HttpServer.create()
            .host("127.0.0.1")
            .port(0)
            .route(routes -> routes
                .post("/agent/resume",
                    (request, response) -> response.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .sendString(Mono.just("{\"session\":{\"id\":\"session-123\"}," + "\"extension_results\":[]}")))
                .post("/sessions/session-123/reply",
                    (request, response) -> response.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .sendString(Mono.just("{\"request_id\":" + "\"00000000-0000-0000-0000-000000000001\"}")))
                .get("/sessions/session-123/events",
                    (request, response) -> response.header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                        .sendString(Flux.just("data: {\"type\":\"ActiveRequests\"," + "\"request_ids\":[]}\n\n", "")
                            .delayElements(Duration.ofMillis(100)))))
            .bindNow();

        try {
            InstanceManager instanceManager = mock(InstanceManager.class);
            HookPipeline hookPipeline = mock(HookPipeline.class);
            AgentConfigService agentConfigService = mock(AgentConfigService.class);
            FileService fileService = mock(FileService.class);
            ManagedInstance instance =
                new ManagedInstance("test-agent", "alice", server.port(), 12345L, null, "test-secret");
            instance.setStatus(ManagedInstance.Status.RUNNING);

            List<Map<String, Object>> beforeFiles = Collections.emptyList();
            List<Map<String, Object>> afterFiles =
                List.of(Map.of("path", "goose-intro.md", "name", "goose-intro.md", "type", "md", "rootId", "workingDir",
                    "displayPath", "goose-intro.md", "size", 16, "modifiedAt", "2026-04-25T00:00:00Z"));
            List<Map<String, String>> changedFiles = List.of(Map.of("path", "goose-intro.md", "name", "goose-intro.md",
                "ext", "md", "rootId", "workingDir", "displayPath", "goose-intro.md"));

            when(instanceManager.getOrSpawn("test-agent", "alice")).thenReturn(Mono.just(instance));
            when(hookPipeline.executeRequest(any(HookContext.class)))
                .thenAnswer(inv -> Mono.just(((HookContext) inv.getArgument(0)).getBody()));
            when(agentConfigService.getUserAgentDir("alice", "test-agent")).thenReturn(Path.of("."));
            when(fileService.listCapsuleRelevantFiles(any())).thenReturn(beforeFiles, afterFiles);
            when(fileService.diffFiles(anyList(), anyList())).thenReturn(changedFiles);

            GatewayProperties properties = new GatewayProperties();
            properties.setGooseTls(false);
            GoosedProxy goosedProxy = new GoosedProxy(properties);
            ReplyController controller =
                new ReplyController(instanceManager, goosedProxy, hookPipeline, agentConfigService, fileService);
            MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilter((request, response, chain) -> {
                    request.setAttribute(UserContextFilter.USER_ID_ATTR, "alice");
                    chain.doFilter(request, response);
                })
                .build();

            mockMvc.perform(post("/api/gateway/agents/test-agent/sessions/session-123/reply")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"request_id\":\"00000000-0000-0000-0000-000000000001\",\"user_message\":"
                        + "{\"role\":\"user\",\"created\":1776928807,\"content\":[{\"type\":\"text\",\"text\":"
                        + "\"create a file\"}],\"metadata\":{\"userVisible\":true,\"agentVisible\":true}}}"))
                .andExpect(status().isOk());

            MvcResult initialResult = mockMvc.perform(get("/api/gateway/agents/test-agent/sessions/session-123/events")
                    .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

            MvcResult result = mockMvc.perform(asyncDispatch(initialResult))
                .andExpect(status().isOk())
                .andReturn();

            String eventBody = result.getResponse().getContentAsString();
            org.junit.Assert.assertTrue("Event body should contain ActiveRequests event",
                eventBody.contains("\"type\":\"ActiveRequests\""));
            org.junit.Assert.assertTrue("Event body should contain OutputFiles event",
                eventBody.contains("\"type\":\"OutputFiles\""));
            org.junit.Assert.assertTrue("Event body should contain request_id",
                eventBody.contains("\"request_id\":\"00000000-0000-0000-0000-000000000001\""));
        } finally {
            server.disposeNow();
        }
    }

    /**
     * Executes the session reply invalid json body still returns gateway error envelope operation.
     */
    @Test
    public void sessionReply_invalidJsonBodyStillReturnsGatewayErrorEnvelope() throws Exception {
        InstanceManager instanceManager = mock(InstanceManager.class);
        HookPipeline hookPipeline = mock(HookPipeline.class);
        AgentConfigService agentConfigService = mock(AgentConfigService.class);
        FileService fileService = mock(FileService.class);

        when(hookPipeline.executeRequest(any(HookContext.class)))
            .thenAnswer(inv -> Mono.just(((HookContext) inv.getArgument(0)).getBody()));
        when(instanceManager.getOrSpawn("test-agent", "alice"))
            .thenReturn(Mono.error(new IllegalStateException("spawn failed")));

        GatewayProperties properties = new GatewayProperties();
        properties.setGooseTls(false);
        GoosedProxy goosedProxy = new GoosedProxy(properties);
        ReplyController controller =
            new ReplyController(instanceManager, goosedProxy, hookPipeline, agentConfigService, fileService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .defaultRequest(post("/").requestAttr(UserContextFilter.USER_ID_ATTR, "alice"))
            .build();

        mockMvc.perform(post("/api/gateway/agents/test-agent/sessions/session-123/reply")
                .contentType(MediaType.APPLICATION_JSON)
                .content("not-json"))
            .andExpect(status().is5xxServerError())
            .andExpect(content().string(org.hamcrest.Matchers.allOf(
                org.hamcrest.Matchers.containsString("gateway_submit_failed"),
                org.hamcrest.Matchers.containsString("session-123"),
                org.hamcrest.Matchers.containsString("test-agent"))));
    }

    /**
     * Executes the session reply snapshot io failure still proxies request operation.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void sessionReply_snapshotIoFailureStillProxiesRequest() throws Exception {
        DisposableServer server = HttpServer.create()
            .host("127.0.0.1")
            .port(0)
            .route(routes -> routes
                .post("/agent/resume",
                    (request, response) -> response.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .sendString(Mono.just("{\"session\":{\"id\":\"session-123\"}," + "\"extension_results\":[]}")))
                .post("/sessions/session-123/reply",
                    (request, response) -> response.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .sendString(Mono.just("{\"ok\":true}"))))
            .bindNow();

        try {
            InstanceManager instanceManager = mock(InstanceManager.class);
            HookPipeline hookPipeline = mock(HookPipeline.class);
            AgentConfigService agentConfigService = mock(AgentConfigService.class);
            FileService fileService = mock(FileService.class);
            ManagedInstance instance =
                new ManagedInstance("test-agent", "alice", server.port(), 12345L, null, "test-secret");
            instance.setStatus(ManagedInstance.Status.RUNNING);

            when(instanceManager.getOrSpawn("test-agent", "alice")).thenReturn(Mono.just(instance));
            when(hookPipeline.executeRequest(any(HookContext.class)))
                .thenAnswer(inv -> Mono.just(((HookContext) inv.getArgument(0)).getBody()));
            when(agentConfigService.getUserAgentDir("alice", "test-agent")).thenReturn(Path.of("."));
            when(fileService.listCapsuleRelevantFiles(any())).thenThrow(new IllegalStateException("disk busy"));

            GatewayProperties properties = new GatewayProperties();
            properties.setGooseTls(false);
            GoosedProxy goosedProxy = new GoosedProxy(properties);
            ReplyController controller =
                new ReplyController(instanceManager, goosedProxy, hookPipeline, agentConfigService, fileService);
            MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilter((request, response, chain) -> {
                    request.setAttribute(UserContextFilter.USER_ID_ATTR, "alice");
                    chain.doFilter(request, response);
                })
                .build();

            mockMvc.perform(post("/api/gateway/agents/test-agent/sessions/session-123/reply")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"request_id\":\"00000000-0000-0000-0000-000000000001\",\"user_message\":{"
                        + "\"role\":\"user\",\"created\":1776928807}}"))
                .andExpect(status().isOk());
        } finally {
            server.disposeNow();
        }
    }
}
