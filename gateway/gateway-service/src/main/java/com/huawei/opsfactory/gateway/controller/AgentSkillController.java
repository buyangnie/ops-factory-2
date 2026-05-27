/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.service.AgentSkillInstallService;
import com.huawei.opsfactory.gateway.service.SkillInstallConflictException;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.servicecomb.provider.rest.common.RestSchema;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST controller for installing and uninstalling skills on agent instances.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RestController
@RestSchema(schemaId = "agentSkillController")
@RequestMapping("/gateway/agents")
public class AgentSkillController {
    private final AgentSkillInstallService installService;

    /**
     * Creates the agent skill controller instance.
     *
     * @param installService service handling skill install/uninstall operations
     */
    public AgentSkillController(AgentSkillInstallService installService) {
        this.installService = installService;
    }

    /**
     * Installs a skill on the specified agent instance.
     *
     * @param agentId agent instance identifier
     * @param body request body containing "skillId"
     * @param request current HTTP request
     * @return ResponseEntity with installation result
     */
    @PostMapping("/{agentId}/skills/install")
    public ResponseEntity<Map<String, Object>> installSkill(@PathVariable("agentId") String agentId,
        @RequestBody Map<String, String> body, HttpServletRequest request) {
        String skillId = body.get("skillId");
        try {
            return ResponseEntity.ok(installService.install(agentId, skillId));
        } catch (SkillInstallConflictException e) {
            return conflict(e.getMessage());
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (ResponseStatusException | IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                e.getMessage() == null ? "Failed to install skill" : e.getMessage(), e);
        }
    }

    /**
     * Uninstalls a skill from the specified agent instance.
     *
     * @param agentId agent instance identifier
     * @param skillId skill identifier to remove
     * @param request current HTTP request
     * @return ResponseEntity with uninstallation result
     */
    @DeleteMapping("/{agentId}/skills/{skillId}")
    public ResponseEntity<Map<String, Object>> uninstallSkill(@PathVariable("agentId") String agentId,
        @PathVariable("skillId") String skillId, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(installService.uninstall(agentId, skillId));
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (ResponseStatusException | IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                e.getMessage() == null ? "Failed to uninstall skill" : e.getMessage(), e);
        }
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", message);
        return ResponseEntity.badRequest().body(body);
    }

    private ResponseEntity<Map<String, Object>> conflict(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", message);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}