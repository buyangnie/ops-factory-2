/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.service.channel.ChannelAdapterRegistry;
import com.huawei.opsfactory.gateway.service.channel.ChannelConfigService;
import com.huawei.opsfactory.gateway.service.channel.WeChatLoginService;
import com.huawei.opsfactory.gateway.service.channel.WhatsAppMessagePumpService;
import com.huawei.opsfactory.gateway.service.channel.WhatsAppWebLoginService;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelDetail;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelLoginState;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelSelfTestRequest;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelSelfTestResult;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelUpsertRequest;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelVerificationResult;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.servicecomb.provider.rest.common.RestSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin REST controller for managing external channel configurations and login lifecycle.
 *
 * @author x00000000
 * @since 2026-05-09
 */

@RestController
@RestSchema(schemaId = "channelAdminController")
@RequestMapping("/gateway/channels")
public class ChannelAdminController {
    private static final Logger log = LoggerFactory.getLogger(ChannelAdminController.class);

    private final ChannelConfigService channelConfigService;

    private final ChannelAdapterRegistry channelAdapterRegistry;

    private final WhatsAppWebLoginService whatsAppWebLoginService;

    private final WhatsAppMessagePumpService whatsAppMessagePumpService;

    private final WeChatLoginService weChatLoginService;

    /**
     * Creates the channel admin controller instance.
     */
    public ChannelAdminController(ChannelConfigService channelConfigService,
        ChannelAdapterRegistry channelAdapterRegistry, WhatsAppWebLoginService whatsAppWebLoginService,
        WhatsAppMessagePumpService whatsAppMessagePumpService, WeChatLoginService weChatLoginService) {
        this.channelConfigService = channelConfigService;
        this.channelAdapterRegistry = channelAdapterRegistry;
        this.whatsAppWebLoginService = whatsAppWebLoginService;
        this.whatsAppMessagePumpService = whatsAppMessagePumpService;
        this.weChatLoginService = weChatLoginService;
    }

    /**
     * Lists all channels for the current admin user.
     *
     * @param exchange server web exchange
     * @return the result
     */
    @GetMapping
    public Map<String, Object> listChannels(HttpServletRequest request) {
        String userId = currentUserId(request);
        return Map.of("channels", channelConfigService.listChannels(userId));
    }

    /**
     * Gets a channel by ID.
     *
     * @param channelId channel identifier
     * @param exchange server web exchange
     * @return a channel by ID
     */
    @GetMapping("/{channelId}")
    public ResponseEntity<ChannelDetail> getChannel(@PathVariable("channelId") String channelId,
        HttpServletRequest request) {
        String userId = currentUserId(request);
        ChannelDetail detail = channelConfigService.getChannel(channelId, userId);
        if (detail == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(detail);
    }

    /**
     * Creates a new channel.
     *
     * @param request HTTP request
     * @param exchange server web exchange
     * @return the result
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createChannel(@RequestBody ChannelUpsertRequest requestBody,
        HttpServletRequest request) {
        String ownerUserId = currentUserId(request);
        try {
            ChannelDetail detail =
                channelConfigService.createChannel(requestBody, ownerUserId != null ? ownerUserId : "admin");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true, "channel", detail));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(invalidChannelRequestBody());
        }
    }

    /**
     * Updates a channel by ID.
     *
     * @param channelId a channel by ID
     * @param request a channel by ID
     * @param exchange a channel by ID
     * @return the result
     */
    @PutMapping("/{channelId}")
    public ResponseEntity<Map<String, Object>> updateChannel(@PathVariable("channelId") String channelId,
        @RequestBody ChannelUpsertRequest requestBody, HttpServletRequest request) {
        String userId = currentUserId(request);
        try {
            ChannelDetail detail = channelConfigService.updateChannel(channelId, requestBody, userId);
            return ResponseEntity.ok(Map.of("success", true, "channel", detail));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(invalidChannelRequestBody());
        }
    }

    /**
     * Enables a channel by ID.
     *
     * @param channelId enables a channel by ID
     * @param exchange enables a channel by ID
     * @return the enables a channel by ID
     */
    @PostMapping("/{channelId}/enable")
    public ResponseEntity<Map<String, Object>> enableChannel(@PathVariable("channelId") String channelId,
        HttpServletRequest request) {
        return setEnabled(channelId, true, request);
    }

    /**
     * Disables a channel by ID.
     *
     * @param channelId disables a channel by ID
     * @param exchange disables a channel by ID
     * @return the disables a channel by ID
     */
    @PostMapping("/{channelId}/disable")
    public ResponseEntity<Map<String, Object>> disableChannel(@PathVariable("channelId") String channelId,
        HttpServletRequest request) {
        return setEnabled(channelId, false, request);
    }

    /**
     * Deletes a channel by ID.
     *
     * @param channelId channel identifier
     * @param exchange server web exchange
     * @return the result
     */
    @DeleteMapping("/{channelId}")
    public ResponseEntity<Map<String, Object>> deleteChannel(@PathVariable("channelId") String channelId,
        HttpServletRequest request) {
        try {
            channelConfigService.deleteChannel(channelId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(invalidChannelRequestBody());
        }
    }

    /**
     * Lists all bindings for a channel.
     *
     * @param channelId channel identifier
     * @param exchange server web exchange
     * @return the result
     */
    @GetMapping("/{channelId}/bindings")
    public ResponseEntity<Map<String, Object>> listBindings(@PathVariable("channelId") String channelId,
        HttpServletRequest request) {
        String userId = currentUserId(request);
        try {
            return ResponseEntity.ok(Map.of("bindings", channelConfigService.listBindings(channelId, userId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(invalidChannelRequestBody());
        }
    }

    /**
     * Lists all events for a channel.
     *
     * @param channelId channel identifier
     * @param exchange server web exchange
     * @return the result
     */
    @GetMapping("/{channelId}/events")
    public ResponseEntity<Map<String, Object>> listEvents(@PathVariable("channelId") String channelId,
        HttpServletRequest request) {
        String userId = currentUserId(request);
        try {
            return ResponseEntity.ok(Map.of("events", channelConfigService.listEvents(channelId, userId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(invalidChannelRequestBody());
        }
    }

    /**
     * Verifies a channel configuration.
     *
     * @param channelId verifies a channel configuration
     * @param exchange verifies a channel configuration
     * @return the verifies a channel configuration
     */
    @PostMapping("/{channelId}/verify")
    public ResponseEntity<Map<String, Object>> verifyChannel(@PathVariable("channelId") String channelId,
        HttpServletRequest request) {
        String userId = currentUserId(request);
        try {
            ChannelVerificationResult result = channelConfigService.verifyChannel(channelId, userId);
            return ResponseEntity.ok(Map.of("success", result.ok(), "verification", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(invalidChannelRequestBody());
        }
    }

    /**
     * Probes a channel for connectivity status.
     *
     * @param channelId probes a channel for connectivity status
     * @param exchange probes a channel for connectivity status
     * @return the probes a channel for connectivity status
     */
    @PostMapping("/{channelId}/probe")
    public ResponseEntity<Map<String, Object>> probeChannel(@PathVariable("channelId") String channelId,
        HttpServletRequest request) {
        String userId = currentUserId(request);
        ChannelDetail detail = channelConfigService.getChannel(channelId, userId);
        if (detail == null) {
            return ResponseEntity.badRequest().body(errorBody("Channel '" + channelId + "' not found"));
        }
        try {
            com.huawei.opsfactory.gateway.service.channel.model.ChannelConnectivityResult result =
                channelAdapterRegistry.require(detail.type()).testConnectivity(channelId, userId).block();
            return ResponseEntity.ok(Map.of("success", result.ok(), "connectivity", result));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(errorBody("Channel probe failed: " + e.getMessage()));
        }
    }

    /**
     * Gets the current login state for a channel.
     *
     * @param channelId channel identifier
     * @param exchange server web exchange
     * @return the current login state for a channel
     */
    @GetMapping("/{channelId}/login-state")
    public ResponseEntity<Map<String, Object>> getLoginState(@PathVariable("channelId") String channelId,
        HttpServletRequest request) {
        String userId = currentUserId(request);
        try {
            ChannelDetail detail = channelConfigService.getChannel(channelId, userId);
            if (detail == null) {
                return ResponseEntity.badRequest().body(errorBody("Channel '" + channelId + "' not found"));
            }
            ChannelLoginState state = switch (detail.type()) {
                case "wechat":
                    yield weChatLoginService.getLoginState(channelId, userId);
                case "whatsapp":
                    yield whatsAppWebLoginService.getLoginState(channelId, userId);
                default:
                    throw new IllegalArgumentException(detail.type() + " login is not implemented yet");
            };
            return ResponseEntity.ok(Map.of("state", state));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(invalidChannelRequestBody());
        }
    }

    /**
     * Starts the login process for a channel.
     *
     * @param channelId channel identifier
     * @param exchange server web exchange
     * @return the starts the login process for a channel
     */
    @PostMapping("/{channelId}/login")
    public ResponseEntity<Map<String, Object>> startLogin(@PathVariable("channelId") String channelId,
        HttpServletRequest request) {
        String userId = currentUserId(request);
        try {
            ChannelDetail detail = channelConfigService.getChannel(channelId, userId);
            if (detail == null) {
                return ResponseEntity.badRequest().body(errorBody("Channel '" + channelId + "' not found"));
            }
            ChannelLoginState state = switch (detail.type()) {
                case "wechat":
                    yield weChatLoginService.startLogin(channelId, userId);
                case "whatsapp":
                    yield whatsAppWebLoginService.startLogin(channelId, userId);
                default:
                    throw new IllegalArgumentException(detail.type() + " login is not implemented yet");
            };
            return ResponseEntity.ok(Map.of("success", true, "state", state));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(invalidChannelRequestBody());
        }
    }

    /**
     * Logs out from a channel and resets its runtime state.
     *
     * @param channelId logs out from a channel and resets its runtime state
     * @param exchange logs out from a channel and resets its runtime state
     * @return the logs out from a channel and resets its runtime state
     */
    @PostMapping("/{channelId}/logout")
    public ResponseEntity<Map<String, Object>> logout(@PathVariable("channelId") String channelId,
        HttpServletRequest request) {
        String userId = currentUserId(request);
        try {
            ChannelDetail detail = channelConfigService.getChannel(channelId, userId);
            if (detail == null) {
                return ResponseEntity.badRequest().body(errorBody("Channel '" + channelId + "' not found"));
            }
            if ("wechat".equals(detail.type())) {
                weChatLoginService.logout(channelId, userId);
            } else if ("whatsapp".equals(detail.type())) {
                whatsAppWebLoginService.logout(channelId, userId);
            } else {
                return ResponseEntity.badRequest().body(errorBody(detail.type() + " login is not implemented yet"));
            }
            detail = channelConfigService.resetChannelRuntimeState(channelId, userId);
            String disconnectedMessage =
                "wechat".equals(detail.type()) ? "WeChat login required" : "WhatsApp Web login required";
            ChannelLoginState state =
                new ChannelLoginState(detail.id(), "disconnected", disconnectedMessage, detail.config().authStateDir(),
                    "wechat".equals(detail.type()) ? detail.config().wechatId() : detail.config().selfPhone(),
                    detail.config().lastConnectedAt(), detail.config().lastDisconnectedAt(),
                    detail.config().lastError(), null);
            return ResponseEntity.ok(Map.of("success", true, "state", state));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(invalidChannelRequestBody());
        } catch (IllegalStateException e) {
            log.error("Failed to logout channel {}", channelId, e);
            ChannelDetail detail = null;
            try {
                detail = channelConfigService.getChannel(channelId, userId);
                if (detail != null) {
                    detail = channelConfigService.resetChannelRuntimeState(channelId, userId);
                }
            } catch (IllegalArgumentException resetError) {
                log.warn("Failed to reset runtime state for channel {} after logout error: {}", channelId,
                    resetError.getMessage());
            }
            if (detail == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorBody("Failed to clear channel login state"));
            }
            String disconnectedMessage =
                "wechat".equals(detail.type()) ? "WeChat login required" : "WhatsApp Web login required";
            ChannelLoginState fallbackState = new ChannelLoginState(detail.id(), "disconnected", disconnectedMessage,
                detail.config().authStateDir(), "wechat".equals(detail.type()) ? detail.config().wechatId() : "",
                detail.config().lastConnectedAt(), detail.config().lastDisconnectedAt(), "", null);
            return ResponseEntity.ok(Map.of("success", true, "state", fallbackState));
        }
    }

    /**
     * Runs a self-test on a channel to verify end-to-end messaging.
     *
     * @param channelId runs a self-test on a channel to verify end-to-end messaging
     * @param request runs a self-test on a channel to verify end-to-end messaging
     * @param exchange runs a self-test on a channel to verify end-to-end messaging
     * @return the runs a self-test on a channel to verify end-to-end messaging
     */
    @PostMapping("/{channelId}/self-test")
    public ResponseEntity<Map<String, Object>> runSelfTest(@PathVariable("channelId") String channelId,
        @RequestBody ChannelSelfTestRequest requestBody, HttpServletRequest request) {
        String userId = currentUserId(request);
        try {
            ChannelDetail detail = channelConfigService.getChannel(channelId, userId);
            if (detail == null) {
                return ResponseEntity.badRequest().body(errorBody("Channel '" + channelId + "' not found"));
            }
            if ("wechat".equals(detail.type())) {
                return ResponseEntity.badRequest().body(errorBody("wechat self-test is not implemented yet"));
            }
            if (!"whatsapp".equals(detail.type())) {
                return ResponseEntity.badRequest().body(errorBody(detail.type() + " self-test is not implemented yet"));
            }
            ChannelSelfTestResult result =
                whatsAppMessagePumpService.runSelfTest(channelId, userId, requestBody.text());
            return ResponseEntity.ok(Map.of("success", true, "result", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(invalidChannelRequestBody());
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(channelSelfTestFailedBody());
        }
    }

    private ResponseEntity<Map<String, Object>> setEnabled(String channelId, boolean enabled,
        HttpServletRequest request) {
        String userId = currentUserId(request);
        try {
            ChannelDetail detail = channelConfigService.setEnabled(channelId, enabled, userId);
            return ResponseEntity.ok(Map.of("success", true, "channel", detail));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(invalidChannelRequestBody());
        }
    }

    private Map<String, Object> invalidChannelRequestBody() {
        return errorBody("Invalid channel request");
    }

    private Map<String, Object> channelSelfTestFailedBody() {
        return errorBody("Channel self-test failed");
    }

    private Map<String, Object> errorBody(String error) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", error);
        return body;
    }

    private String currentUserId(HttpServletRequest request) {
        String userId = (String) request.getAttribute(UserContextFilter.USER_ID_ATTR);
        return userId == null || userId.isBlank() ? "admin" : userId;
    }
}
