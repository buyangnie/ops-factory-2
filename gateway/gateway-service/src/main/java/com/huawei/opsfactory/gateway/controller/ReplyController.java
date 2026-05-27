/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.common.model.ManagedInstance;
import com.huawei.opsfactory.gateway.common.util.JsonUtil;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.hook.HookContext;
import com.huawei.opsfactory.gateway.hook.HookPipeline;
import com.huawei.opsfactory.gateway.process.InstanceManager;
import com.huawei.opsfactory.gateway.proxy.GoosedProxy;
import com.huawei.opsfactory.gateway.service.AgentConfigService;
import com.huawei.opsfactory.gateway.service.FileService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;

import org.apache.servicecomb.provider.rest.common.RestSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Core session controller handling chat replies, SSE event streams, session resume, and restart.
 *
 * @author x00000000
 * @since 2026-05-09
 */

@RestController
@RestSchema(schemaId = "replyController")
@RequestMapping("/gateway/agents/{agentId}")
public class ReplyController {
    private static final Logger log = LoggerFactory.getLogger(ReplyController.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Map<String, String> SESSION_ERROR_MESSAGE_KEYS;

    static {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("gateway_submit_timeout", "chat.sessionErrors.gatewaySubmitTimeout");
        map.put("gateway_submit_failed", "chat.sessionErrors.gatewaySubmitFailed");
        map.put("gateway_events_failed", "chat.sessionErrors.gatewayEventsFailed");
        map.put("gateway_cancel_failed", "chat.sessionErrors.gatewayCancelFailed");
        map.put("gateway_unauthorized", "chat.sessionErrors.gatewayUnauthorized");
        map.put("gateway_agent_not_found", "chat.sessionErrors.gatewayAgentNotFound");
        map.put("gateway_agent_unavailable", "chat.sessionErrors.gatewayAgentUnavailable");
        map.put("gateway_goosed_unavailable", "chat.sessionErrors.gatewayGoosedUnavailable");
        map.put("gateway_max_duration_reached", "chat.sessionErrors.gatewayMaxDurationReached");
        map.put("gateway_rate_limited", "chat.sessionErrors.gatewayRateLimited");
        map.put("goosed_active_request_conflict", "chat.sessionErrors.goosedActiveRequestConflict");
        map.put("goosed_request_rejected", "chat.sessionErrors.goosedRequestRejected");
        map.put("goosed_error", "chat.sessionErrors.goosedError");
        map.put("provider_timeout", "chat.sessionErrors.providerTimeout");
        map.put("provider_rate_limited", "chat.sessionErrors.providerRateLimited");
        map.put("provider_auth_or_quota_failed", "chat.sessionErrors.providerAuthOrQuotaFailed");
        map.put("tool_execution_failed", "chat.sessionErrors.toolExecutionFailed");
        map.put("mcp_unavailable", "chat.sessionErrors.mcpUnavailable");
        map.put("context_too_large", "chat.sessionErrors.contextTooLarge");
        SESSION_ERROR_MESSAGE_KEYS = Collections.unmodifiableMap(map);
    }

    private final InstanceManager instanceManager;

    private final GoosedProxy goosedProxy;

    private final HookPipeline hookPipeline;

    private final AgentConfigService agentConfigService;

    private final FileService fileService;

    private final ConcurrentHashMap<String, String> inFlightResumes = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, List<Map<String, Object>>> fileSnapshots = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, String> pendingFileSnapshotRequests = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);

    /**
     * Creates the reply controller instance.
     *
     * @param instanceManager manages goosed instance lifecycle and lookup
     * @param goosedProxy proxies HTTP requests to the goosed backend
     * @param hookPipeline hook pipeline for request/response transformations
     * @param agentConfigService provides agent configuration and user directories
     * @param fileService file service for workspace file operations
     */
    public ReplyController(InstanceManager instanceManager, GoosedProxy goosedProxy, HookPipeline hookPipeline,
        AgentConfigService agentConfigService, FileService fileService) {
        this.instanceManager = instanceManager;
        this.goosedProxy = goosedProxy;
        this.hookPipeline = hookPipeline;
        this.agentConfigService = agentConfigService;
        this.fileService = fileService;
    }

    /**
     * Submits a chat reply to an active session and proxies the response from goosed.
     *
     * @param agentId unique identifier of the target agent
     * @param sessionId unique identifier of the chat session to reply in
     * @param body JSON request body containing the user message and request metadata
     * @param exchange reactive server exchange providing request attributes and the response sink
     * @return a {@code void} that completes once the proxied reply and SSE stream are finished
     */
    @PostMapping(value = "/sessions/{sessionId}/reply", produces = MediaType.APPLICATION_JSON_VALUE)
    public String sessionReply(@PathVariable("agentId") String agentId, @PathVariable("sessionId") String sessionId,
        @RequestBody String body, HttpServletRequest request) {
        long requestStart = System.currentTimeMillis();
        String userId = (String) request.getAttribute(UserContextFilter.USER_ID_ATTR);
        HookContext ctx = new HookContext(body, agentId, userId);
        log.info("[SESSION-REPLY] request received agentId={} userId={} sessionId={} bodyLen={}", agentId, userId,
            sessionId, body.length());
        String chatRequestId = extractRequestId(body);

        try {
            String processedBody = hookPipeline.executeRequest(ctx).map(this::normalizeReplyUserMessageCreated).block();
            ManagedInstance instance = instanceManager.getOrSpawn(agentId, userId).block();
            instance.touch();
            instanceManager.touchAllForUser(userId);
            String path = goosedSessionPath(sessionId, "reply");
            ensureSessionResumed(instance, sessionId);
            snapshotFilesBeforeReply(agentId, userId, sessionId, chatRequestId);
            log.info("[SESSION-REPLY] forwarding agentId={} userId={} sessionId={} port={} path={}", agentId, userId,
                sessionId, instance.getPort(), path);
            String result = goosedProxy
                .fetchJson(instance.getPort(), HttpMethod.POST, path, processedBody, 120, instance.getSecretKey())
                .block();
            log.info("[SESSION-REPLY] completed agentId={} userId={} sessionId={} totalMs={}", agentId, userId,
                sessionId, System.currentTimeMillis() - requestStart);
            return result;
        } catch (ResponseStatusException | WebClientResponseException | IllegalStateException err) {
            removeFileSnapshot(agentId, userId, sessionId, chatRequestId);
            log.warn("[SESSION-REPLY] failed agentId={} userId={} sessionId={} totalMs={} error={}", agentId, userId,
                sessionId, System.currentTimeMillis() - requestStart, err.getMessage());
            throw err;
        }
    }

    /**
     * Subscribes to the SSE event stream for an active session.
     *
     * @param agentId unique identifier of the target agent
     * @param sessionId unique identifier of the chat session to stream events from
     * @param lastEventId ID of the last received SSE event for resumption, may be {@code null}
     * @param exchange reactive server exchange providing request attributes and the response sink
     * @return a {@code void} that completes when the SSE stream ends or errors out
     */
    @GetMapping(value = "/sessions/{sessionId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sessionEvents(@PathVariable("agentId") String agentId,
        @PathVariable("sessionId") String sessionId,
        @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId, HttpServletRequest request) {
        long requestStart = System.currentTimeMillis();
        String userId = (String) request.getAttribute(UserContextFilter.USER_ID_ATTR);
        log.info("[SESSION-EVENTS] subscribe agentId={} userId={} sessionId={} lastEventId={}", agentId, userId,
            sessionId, lastEventId);

        ManagedInstance instance = instanceManager.getOrSpawn(agentId, userId).block();
        instance.touch();
        instanceManager.touchAllForUser(userId);
        String path = goosedSessionPath(sessionId, "events");
        log.info("[SESSION-EVENTS] forwarding agentId={} userId={} sessionId={} port={} path={}", agentId, userId,
            sessionId, instance.getPort(), path);

        return goosedProxy.proxySessionEventsToEmitter(instance.getPort(), path, instance.getSecretKey(), lastEventId,
            agentId, userId, sessionId);
    }

    /**
     * Cancels an in-progress request within a session.
     *
     * @param agentId unique identifier of the target agent
     * @param sessionId unique identifier of the chat session containing the request to cancel
     * @param body JSON request body with the request ID to cancel
     * @param exchange reactive server exchange providing request attributes and the response sink
     * @return a {@code void} that completes once the cancel command has been proxied
     */
    @PostMapping(value = "/sessions/{sessionId}/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    public String sessionCancel(@PathVariable("agentId") String agentId, @PathVariable("sessionId") String sessionId,
        @RequestBody String body, HttpServletRequest request) {
        long requestStart = System.currentTimeMillis();
        String userId = (String) request.getAttribute(UserContextFilter.USER_ID_ATTR);
        log.info("[SESSION-CANCEL] request received agentId={} userId={} sessionId={} bodyLen={}", agentId, userId,
            sessionId, body.length());
        String requestId = extractRequestId(body);

        try {
            ManagedInstance instance = instanceManager.getOrSpawn(agentId, userId).block();
            instance.touch();
            instanceManager.touchAllForUser(userId);
            String path = goosedSessionPath(sessionId, "cancel");
            log.info("[SESSION-CANCEL] forwarding agentId={} userId={} sessionId={} port={} path={}", agentId, userId,
                sessionId, instance.getPort(), path);
            String result =
                goosedProxy.fetchJson(instance.getPort(), HttpMethod.POST, path, body, 30, instance.getSecretKey())
                    .block();
            log.info("[SESSION-CANCEL] completed agentId={} userId={} sessionId={} totalMs={}", agentId, userId,
                sessionId, System.currentTimeMillis() - requestStart);
            return result;
        } catch (ResponseStatusException | WebClientResponseException | IllegalStateException err) {
            log.warn("[SESSION-CANCEL] failed agentId={} userId={} sessionId={} totalMs={} error={}", agentId, userId,
                sessionId, System.currentTimeMillis() - requestStart, err.getMessage());
            throw err;
        }
    }

    private String goosedSessionPath(String sessionId, String suffix) {
        return "/sessions/" + UriUtils.encodePathSegment(sessionId, StandardCharsets.UTF_8) + "/" + suffix;
    }

    private String extractRequestId(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode rootNode = MAPPER.readTree(body);
            JsonNode requestIdNode = rootNode.get("request_id");
            return requestIdNode != null && requestIdNode.isTextual() ? requestIdNode.asText() : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private void snapshotFilesBeforeReply(String agentId, String userId, String sessionId, String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }
        Path workingDir = agentConfigService.getUserAgentDir(userId, agentId);
        List<Map<String, Object>> beforeFiles = snapshotFiles(workingDir);
        String key = fileSnapshotKey(agentId, userId, sessionId, requestId);
        String sessionKey = fileSnapshotSessionKey(agentId, userId, sessionId);
        fileSnapshots.put(key, beforeFiles);
        pendingFileSnapshotRequests.put(sessionKey, requestId);
        scheduleFileSnapshotRemoval(key, sessionKey, requestId);
        log.debug(
            "[SESSION-REPLY] captured {} file snapshot entries agentId={} userId={} sessionId={} " + "requestId={}",
            beforeFiles.size(), agentId, userId, sessionId, requestId);
    }

    private String outputFilesBeforeTerminalEvent(String agentId, String userId, String sessionId, String eventJson) {
        try {
            JsonNode event = MAPPER.readTree(eventJson);
            String type = event.path("type").asText("");
            boolean activeRequestsDrained = "ActiveRequests".equals(type) && event.path("request_ids").isArray()
                && event.path("request_ids").isEmpty();
            if (!"Finish".equals(type) && !"Error".equals(type) && !activeRequestsDrained) {
                return "";
            }
            String requestId = resolveEventRequestId(agentId, userId, sessionId, event);
            if (requestId == null || requestId.isBlank()) {
                return "";
            }

            String key = fileSnapshotKey(agentId, userId, sessionId, requestId);
            List<Map<String, Object>> beforeFiles = fileSnapshots.remove(key);
            pendingFileSnapshotRequests.remove(fileSnapshotSessionKey(agentId, userId, sessionId), requestId);
            if (beforeFiles == null) {
                return "";
            }

            Path workingDir = agentConfigService.getUserAgentDir(userId, agentId);
            List<Map<String, String>> changed = fileService.diffFiles(beforeFiles, snapshotFiles(workingDir));
            if (changed.isEmpty()) {
                return "";
            }

            String json = MAPPER.writeValueAsString(Map.of("type", "OutputFiles", "sessionId", sessionId, "request_id",
                requestId, "chat_request_id", requestId, "files", changed));
            log.info("[SESSION-EVENTS] detected {} output files agentId={} userId={} sessionId={} requestId={}",
                changed.size(), agentId, userId, sessionId, requestId);
            return "data: " + json + "\n\n";
        } catch (IOException | IllegalStateException err) {
            log.warn("[SESSION-EVENTS] failed to build OutputFiles event agentId={} userId={} sessionId={}: {}",
                agentId, userId, sessionId, err.getMessage());
            return "";
        }
    }

    private String resolveEventRequestId(String agentId, String userId, String sessionId, JsonNode event) {
        String requestId = event.path("chat_request_id").asText(null);
        if (requestId == null || requestId.isBlank()) {
            requestId = event.path("request_id").asText(null);
        }
        if (requestId != null && !requestId.isBlank()) {
            return requestId;
        }
        return pendingFileSnapshotRequests.get(fileSnapshotSessionKey(agentId, userId, sessionId));
    }

    private String fileSnapshotKey(String agentId, String userId, String sessionId, String requestId) {
        return agentId + ":" + userId + ":" + sessionId + ":" + requestId;
    }

    private String fileSnapshotSessionKey(String agentId, String userId, String sessionId) {
        return agentId + ":" + userId + ":" + sessionId;
    }

    private void removeFileSnapshot(String agentId, String userId, String sessionId, String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }
        fileSnapshots.remove(fileSnapshotKey(agentId, userId, sessionId, requestId));
        pendingFileSnapshotRequests.remove(fileSnapshotSessionKey(agentId, userId, sessionId), requestId);
    }

    private void scheduleFileSnapshotRemoval(String key, String sessionKey, String requestId) {
        scheduledExecutorService.schedule(() -> {
            fileSnapshots.remove(key);
            pendingFileSnapshotRequests.remove(sessionKey, requestId);
        }, 5, TimeUnit.MINUTES);
    }

    @PreDestroy
    public void shutdown() {
        scheduledExecutorService.shutdown();
    }

    private List<Map<String, Object>> snapshotFiles(Path workingDir) {
        try {
            List<Map<String, Object>> files = fileService.listCapsuleRelevantFiles(workingDir);
            return files != null ? files : Collections.emptyList();
        } catch (IllegalStateException e) {
            log.debug("[SESSION-REPLY] file snapshot failed (best-effort): {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private HttpStatus sessionErrorStatus(Throwable err) {
        if (err instanceof ResponseStatusException responseStatusException) {
            HttpStatus status = HttpStatus.resolve(responseStatusException.getStatusCode().value());
            return status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (err instanceof WebClientResponseException webClientResponseException) {
            HttpStatus status = HttpStatus.resolve(webClientResponseException.getRawStatusCode());
            return status != null ? status : HttpStatus.BAD_GATEWAY;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private Map<String, Object> sessionErrorBody(Throwable err, HttpStatus status, String agentId, String userId,
        String sessionId, String requestId, String fallbackCode, long elapsedMs) {
        String code = sessionErrorCode(err, status, fallbackCode);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "Error");
        body.put("layer", sessionErrorLayer(err, status));
        body.put("code", code);
        body.put("severity", "error");
        body.put("message_key", sessionErrorMessageKey(code));
        body.put("message", sessionErrorMessage(err, status));
        body.put("detail", err.getMessage());
        body.put("retryable", sessionErrorRetryable(code, status));
        body.put("suggested_actions", sessionSuggestedActions(code));
        body.put("session_id", sessionId);
        body.put("request_id", requestId);
        body.put("agent_id", agentId);
        body.put("user_id", userId);
        body.put("elapsed_ms", elapsedMs);
        body.put("http_status", status.value());
        body.put("trace_id", UUID.randomUUID().toString());
        if (err instanceof WebClientResponseException webClientResponseException) {
            body.put("upstream_status", webClientResponseException.getRawStatusCode());
        }
        return body;
    }

    private String sessionErrorLayer(Throwable err, HttpStatus status) {
        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            return "policy";
        }
        if (err instanceof WebClientResponseException) {
            return "goosed";
        }
        return "gateway";
    }

    private String sessionErrorCode(Throwable err, HttpStatus status, String fallbackCode) {
        if (err instanceof WebClientResponseException) {
            if (status == HttpStatus.CONFLICT) {
                return "goosed_active_request_conflict";
            }
            if (status == HttpStatus.BAD_REQUEST) {
                String message = sessionErrorMessage(err, status);
                if (message != null
                    && message.toLowerCase(Locale.ROOT).contains("session already has an active " + "request")) {
                    return "goosed_active_request_conflict";
                }
                return "goosed_request_rejected";
            }
            return "goosed_error";
        }
        if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
            return "gateway_unauthorized";
        }
        if (status == HttpStatus.NOT_FOUND) {
            return "gateway_agent_not_found";
        }
        if (status == HttpStatus.FAILED_DEPENDENCY) {
            return "gateway_agent_unavailable";
        }
        if (status == HttpStatus.SERVICE_UNAVAILABLE) {
            return "gateway_goosed_unavailable";
        }
        if (status == HttpStatus.GATEWAY_TIMEOUT) {
            if ("gateway_cancel_failed".equals(fallbackCode) || "gateway_events_failed".equals(fallbackCode)) {
                return fallbackCode;
            }
            return "gateway_submit_timeout";
        }
        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            return "gateway_rate_limited";
        }
        return fallbackCode;
    }

    private String sessionErrorMessage(Throwable err, HttpStatus status) {
        if (err instanceof ResponseStatusException responseStatusException
            && responseStatusException.getReason() != null) {
            return responseStatusException.getReason();
        }
        if (err instanceof WebClientResponseException webClientResponseException
            && !webClientResponseException.getResponseBodyAsString().isBlank()) {
            return webClientResponseException.getResponseBodyAsString();
        }
        if (err.getMessage() != null && !err.getMessage().isBlank()) {
            return err.getMessage();
        }
        return status.getReasonPhrase();
    }

    private boolean sessionErrorRetryable(String code, HttpStatus status) {
        if (status.is5xxServerError()) {
            return true;
        }
        return "goosed_active_request_conflict".equals(code) || "gateway_rate_limited".equals(code)
            || "gateway_submit_timeout".equals(code) || "gateway_goosed_unavailable".equals(code);
    }

    private List<String> sessionSuggestedActions(String code) {
        return switch (code) {
            case "gateway_unauthorized":
                yield List.of("contact_support");
            case "gateway_agent_not_found", "gateway_agent_unavailable":
                yield List.of("retry", "contact_support");
            case "gateway_submit_timeout", "gateway_goosed_unavailable", "gateway_rate_limited":
                yield List.of("retry");
            case "goosed_active_request_conflict":
                yield List.of("wait", "cancel", "retry");
            case "goosed_request_rejected":
                yield List.of("retry");
            default:
                yield List.of("contact_support");
        };
    }

    private String sessionErrorMessageKey(String code) {
        return SESSION_ERROR_MESSAGE_KEYS.getOrDefault(code, "chat.sessionErrors.unknown");
    }

    private String normalizeReplyUserMessageCreated(String body) {
        if (body == null || body.isBlank()) {
            return body;
        }
        try {
            JsonNode rootNode = MAPPER.readTree(body);
            if (!(rootNode instanceof ObjectNode)) {
                return body;
            }
            ObjectNode root = (ObjectNode) rootNode;
            JsonNode userMessageNode = root.get("user_message");
            if (!(userMessageNode instanceof ObjectNode)) {
                return body;
            }
            ObjectNode userMessage = (ObjectNode) userMessageNode;
            JsonNode roleNode = userMessage.get("role");
            if (roleNode != null && !"user".equals(roleNode.asText())) {
                return body;
            }

            JsonNode previous = userMessage.get("created");
            Long previousCreated = previous != null && previous.canConvertToLong() ? previous.asLong() : null;
            long created = Instant.now().getEpochSecond();
            userMessage.put("created", created);

            if (log.isDebugEnabled()) {
                Long delta = previousCreated != null ? created - previousCreated : null;
                log.debug("[REPLY] normalized user_message.created old={} new={} deltaSeconds={}", previousCreated,
                    created, delta);
            }
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            log.warn("[REPLY] failed to normalize user_message.created: {}", e.getMessage());
            return body;
        }
    }

    /**
     * Ensure that any follow-up reply against an existing session first restores that
     * session on the current goosed instance (provider + extensions loaded).
     * <p>
     * We intentionally do not trust the gateway's local resumed-session cache here:
     * stop/recycle/page-switch flows can leave the cache and goosed's real state out
     * of sync. Treating resume as the standard precondition for session continuation
     * keeps "continue chatting" aligned with the explicit history/resume entrypoint.
     */
    private void ensureSessionResumed(ManagedInstance instance, String sessionId) {
        if (sessionId == null) {
            log.debug("[REPLY] session id missing, skipping pre-reply resume");
            return;
        }
        String resumeBody = "{\"session_id\":\"" + sessionId + "\",\"load_model_and_extensions\":true}";
        try {
            resumeSession(instance, sessionId, resumeBody, "[REPLY]");
        } catch (WebClientResponseException | IllegalStateException e) {
            log.warn("[REPLY] session {} resume failed on instance {}:{}: {} (will retry next request)", sessionId,
                instance.getAgentId(), instance.getUserId(), e.getMessage());
        }
    }

    private String resumeSession(ManagedInstance instance, String sessionId, String body, String logPrefix) {
        if (sessionId == null || sessionId.isBlank()) {
            return goosedProxy
                .fetchJson(instance.getPort(), HttpMethod.POST, "/agent/resume", body, 120, instance.getSecretKey())
                .block();
        }

        String dedupeKey = instance.getKey() + ":" + sessionId;
        String result = inFlightResumes.get(dedupeKey);
        if (result != null) {
            log.debug("{} joining in-flight resume key={}", logPrefix, dedupeKey);
            return result;
        }

        long resumeStart = System.currentTimeMillis();
        log.info("{} session {} not yet resumed on instance {}:{} (port={}), calling /agent/resume", logPrefix,
            sessionId, instance.getAgentId(), instance.getUserId(), instance.getPort());
        String json = goosedProxy
            .fetchJson(instance.getPort(), HttpMethod.POST, "/agent/resume", body, 120, instance.getSecretKey())
            .block();
        long resumeMs = System.currentTimeMillis() - resumeStart;
        instance.markSessionResumed(sessionId);
        log.info("{} session {} resumed in {}ms on instance {}:{}", logPrefix, sessionId, resumeMs,
            instance.getAgentId(), instance.getUserId());
        inFlightResumes.put(dedupeKey, json);
        return json;
    }

    /**
     * Resumes an existing session, loading model and extensions.
     *
     * @param agentId unique identifier of the target agent
     * @param body JSON request body containing the session ID and resume options
     * @param exchange reactive server exchange providing request attributes for user lookup
     * @return a {@code String} emitting the JSON response from the goosed resume endpoint
     */
    @PostMapping(value = "/agent/resume", produces = MediaType.APPLICATION_JSON_VALUE)
    public String resume(@PathVariable("agentId") String agentId, @RequestBody String body,
        HttpServletRequest request) {
        String userId = (String) request.getAttribute(UserContextFilter.USER_ID_ATTR);
        String sessionId = JsonUtil.extractSessionId(body);
        ManagedInstance instance = instanceManager.getOrSpawn(agentId, userId).block();
        try {
            String json = resumeSession(instance, sessionId, body, "[RESUME]");
            logResumeConversationDigest(agentId, userId, sessionId, json);
            return json;
        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
            }
            throw e;
        }
    }

    private void logResumeConversationDigest(String agentId, String userId, String sessionId, String json) {
        if (!log.isDebugEnabled()) {
            return;
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode sessionNode = root.get("session");
            if (sessionNode == null || sessionNode.isNull()) {
                log.debug("[CHAT-ORDER] resume agentId={} userId={} sessionId={} noSessionNode jsonLen={}", agentId,
                    userId, sessionId, json != null ? json.length() : 0);
                return;
            }
            JsonNode conv = sessionNode.get("conversation");
            if (conv == null || !conv.isArray()) {
                log.debug("[CHAT-ORDER] resume agentId={} userId={} sessionId={} noConversation jsonLen={}", agentId,
                    userId, sessionId, json != null ? json.length() : 0);
                return;
            }

            int total = conv.size();
            int limit = Math.min(total, 30);
            List<String> head = new ArrayList<>(limit);
            int createdCount = 0;
            int inversionCount = 0;
            String prevRole = null;

            for (int i = 0; i < total; i++) {
                JsonNode m = conv.get(i);
                String role = m.hasNonNull("role") ? m.get("role").asText() : "";

                if (prevRole != null && "assistant".equals(prevRole) && "user".equals(role)) {
                    inversionCount++;
                }
                if ("user".equals(role) || "assistant".equals(role)) {
                    prevRole = prevRole == null ? role : prevRole;
                }
                prevRole = role;

                if (hasCreatedTimestamp(m)) {
                    createdCount++;
                }

                if (i < limit) {
                    head.add(buildDigestEntry(i, m, role));
                }
            }

            log.debug(
                "[CHAT-ORDER] resume agentId={} userId={} sessionId={} total={} createdCount={} "
                    + "inversionCount={} head={}",
                agentId, userId, sessionId, total, createdCount, inversionCount, head);
        } catch (JsonProcessingException e) {
            log.debug("[CHAT-ORDER] resume agentId={} userId={} sessionId={} parseFailed err={}", agentId, userId,
                sessionId, e.getMessage());
        }
    }

    private static boolean hasCreatedTimestamp(JsonNode message) {
        JsonNode created = message.get("created");
        if (created != null && created.isNumber()) {
            return true;
        }
        if (created != null && created.isTextual() && !created.asText().trim().isEmpty()) {
            return true;
        }
        if (message.hasNonNull("created_at") || message.hasNonNull("createdAt")) {
            return true;
        }
        return false;
    }

    private static String buildDigestEntry(int index, JsonNode message, String role) {
        String id = message.hasNonNull("id") ? message.get("id").asText() : "";
        String createdRaw = resolveCreatedRaw(message);
        return index + ":" + role + ":" + id + ":" + createdRaw;
    }

    private static String resolveCreatedRaw(JsonNode message) {
        JsonNode created = message.get("created");
        if (created != null && !created.isNull()) {
            String text = created.asText();
            if (!text.isEmpty()) {
                return text;
            }
        }
        if (message.hasNonNull("created_at")) {
            return message.get("created_at").asText();
        }
        if (message.hasNonNull("createdAt")) {
            return message.get("createdAt").asText();
        }
        return "";
    }

    /**
     * Restarts the agent instance with a fresh configuration.
     *
     * @param agentId unique identifier of the target agent
     * @param body JSON request body containing restart parameters
     * @param exchange reactive server exchange providing request attributes and the response sink
     * @return the restarts the agent instance with a fresh configuration
     */
    @PostMapping(value = "/agent/restart", produces = MediaType.APPLICATION_JSON_VALUE)
    public String restart(@PathVariable("agentId") String agentId, @RequestBody String body,
        HttpServletRequest request) {
        String userId = (String) request.getAttribute(UserContextFilter.USER_ID_ATTR);
        ManagedInstance instance = instanceManager.getOrSpawn(agentId, userId).block();
        String result = goosedProxy
            .fetchJson(instance.getPort(), HttpMethod.POST, "/agent/restart", body, 30, instance.getSecretKey())
            .block();
        return result;
    }
}
