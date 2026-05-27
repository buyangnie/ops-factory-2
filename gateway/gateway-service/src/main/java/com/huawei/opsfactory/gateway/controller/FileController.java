/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.common.util.PathSanitizer;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.process.InstanceManager;
import com.huawei.opsfactory.gateway.service.AgentConfigService;
import com.huawei.opsfactory.gateway.service.FileService;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.servicecomb.provider.rest.common.RestSchema;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for browsing, uploading, downloading, and editing agent workspace files.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RestController
@RestSchema(schemaId = "fileController")
@RequestMapping("/gateway/agents/{agentId}")
public class FileController {
    private final InstanceManager instanceManager;

    private final AgentConfigService agentConfigService;

    private final FileService fileService;

    /**
     * Creates the file controller instance.
     */
    public FileController(InstanceManager instanceManager, AgentConfigService agentConfigService,
        FileService fileService) {
        this.instanceManager = instanceManager;
        this.agentConfigService = agentConfigService;
        this.fileService = fileService;
    }

    /**
     * Lists files in the agent workspace directory.
     *
     * @param agentId agent identifier
     * @param request current HTTP request
     * @return the result
     */
    @GetMapping(value = "/files/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> listFiles(@PathVariable("agentId") String agentId, HttpServletRequest request)
        throws IOException {
        String userId = (String) request.getAttribute(UserContextFilter.USER_ID_ATTR);
        Path workingDir = agentConfigService.getUserAgentDir(userId, agentId);
        return Map.of("files", fileService.listFiles(workingDir));
    }

    /**
     * Downloads or retrieves a file from the agent workspace.
     *
     * @param agentId agent identifier
     * @param path file path relative to working directory
     * @param download force download flag
     * @param request current HTTP request
     * @return the download or retrieve result
     */
    @GetMapping(value = "/files/get")
    public ResponseEntity<?> getFile(@PathVariable("agentId") String agentId, @RequestParam String path,
        @RequestParam(defaultValue = "false") boolean download, HttpServletRequest request) {
        String userId = (String) request.getAttribute(UserContextFilter.USER_ID_ATTR);
        Path workingDir = agentConfigService.getUserAgentDir(userId, agentId);
        String relativePath = URLDecoder.decode(path, StandardCharsets.UTF_8);

        // Check for path traversal
        if (!PathSanitizer.isSafe(workingDir, relativePath)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "path traversal not allowed"));
        }

        try {
            Resource resource = fileService.resolveFile(workingDir, relativePath);
            if (resource == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "file not found"));
            }

            String filename = resource.getFilename();
            String mimeType = fileService.getMimeType(filename != null ? filename : "");
            String disposition = (!download && fileService.isInline(mimeType)) ? "inline" : "attachment";

            byte[] content;
            try (InputStream is = resource.getInputStream()) {
                content = is.readAllBytes();
            }

            String encodedFilename =
                java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            String contentDisposition =
                disposition + "; filename=\"" + filename + "\"; filename*=UTF-8''" + encodedFilename;
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .body(content);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read file", e);
        }
    }

    /**
     * Deletes a file from the agent workspace.
     *
     * @param agentId agent identifier
     * @param path file path relative to working directory
     * @param rootId optional root identifier
     * @param request current HTTP request
     * @return the deletion result
     */
    @DeleteMapping(value = "/files/delete")
    public ResponseEntity<Map<String, Object>> deleteFile(@PathVariable("agentId") String agentId,
        @RequestParam String path, @RequestParam(required = false) String rootId, HttpServletRequest request)
        throws IOException {
        String userId = (String) request.getAttribute(UserContextFilter.USER_ID_ATTR);
        Path workingDir = agentConfigService.getUserAgentDir(userId, agentId);
        String relativePath = URLDecoder.decode(path, StandardCharsets.UTF_8);

        if (!PathSanitizer.isSafe(workingDir, relativePath)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "path traversal not allowed"));
        }

        boolean deleted = fileService.deleteFile(workingDir, relativePath);
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "file not found"));
        }
        return ResponseEntity.ok(Map.of("status", "deleted", "path", relativePath));
    }

    /**
     * Updates the content of an editable text file in the agent workspace.
     *
     * @param agentId agent identifier
     * @param path file path relative to working directory
     * @param requestBody the content of an editable text file
     * @param request current HTTP request
     * @return the update result
     */
    @PutMapping(value = "/files/update", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> updateFile(@PathVariable("agentId") String agentId,
        @RequestParam String path, @RequestBody FileUpdateRequest requestBody, HttpServletRequest request) {
        String userId = (String) request.getAttribute(UserContextFilter.USER_ID_ATTR);
        Path workingDir = agentConfigService.getUserAgentDir(userId, agentId);
        String relativePath = URLDecoder.decode(path, StandardCharsets.UTF_8);

        if (!PathSanitizer.isSafe(workingDir, relativePath)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "path traversal not allowed"));
        }

        if (!fileService.isEditableTextFile(relativePath)) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(Map.of("error", "file type is not editable"));
        }

        boolean updated = fileService.updateTextFile(workingDir, relativePath, requestBody.content());
        if (!updated) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "file not found"));
        }
        return ResponseEntity.ok(Map.of("status", "updated", "path", relativePath));
    }

    /**
     * Uploads a file to the agent workspace for a specific session.
     *
     * @param agentId uploads a file to the agent workspace for a specific session
     * @param filePart uploads a file to the agent workspace for a specific session
     * @param sessionId uploads a file to the agent workspace for a specific session
     * @param request current HTTP request
     * @return the upload result
     */
    @PostMapping(value = "/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadFile(@PathVariable("agentId") String agentId,
        @RequestPart("file") MultipartFile filePart, @RequestPart("sessionId") String sessionId,
        HttpServletRequest request) {
        String userId = (String) request.getAttribute(UserContextFilter.USER_ID_ATTR);
        Path uploadsDir = agentConfigService.getUserAgentDir(userId, agentId).resolve("uploads").resolve(sessionId);

        String originalName = filePart.getOriginalFilename();

        // Check file type
        if (!fileService.isAllowedExtension(originalName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File type not allowed: " + originalName);
        }

        try {
            Files.createDirectories(uploadsDir);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create upload dir");
        }

        String safeName = System.currentTimeMillis() + "_" + PathSanitizer.sanitizeFilename(originalName);
        Path dest = uploadsDir.resolve(safeName);
        String mimeType = fileService.getMimeType(originalName);

        try {
            filePart.transferTo(dest);
            Map<String, Object> result = new HashMap<>();
            result.put("status", "uploaded");
            result.put("filename", safeName);
            result.put("path", dest.toString());
            result.put("name", PathSanitizer.sanitizeFilename(originalName));
            result.put("type", mimeType);
            result.put("size", Files.size(dest));
            return result;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to upload file", e);
        }
    }

    /**
     * Fallback for upload requests that are not multipart/form-data.
     */
    @PostMapping(value = "/files/upload/error", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> uploadFileNotMultipart() {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload requires multipart/form-data content type");
    }

    private record FileUpdateRequest(String content) {
    }
}
