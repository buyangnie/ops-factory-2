/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.common.model.ImportError;
import com.huawei.opsfactory.gateway.common.model.ImportResult;
import com.huawei.opsfactory.gateway.service.ImportExportService;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.servicecomb.provider.rest.common.RestSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for import and export operations.
 *
 * @since 2026-05-28
 */
@RestController
@RestSchema(schemaId = "importExportController")
@RequestMapping("/gateway/import-export")
public class ImportExportController {

    private static final Logger log = LoggerFactory.getLogger(ImportExportController.class);

    private final ImportExportService importExportService;

    public ImportExportController(ImportExportService importExportService) {
        this.importExportService = importExportService;
    }

    /**
     * Validates an uploaded Excel file structure.
     *
     * @param file the uploaded file
     * @param resourceType the resource type
     * @param request HTTP request
     * @return validation result
     */
    @PostMapping(value = "/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> validateFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("resourceType") String resourceType,
            HttpServletRequest request) {

        log.info("Validating import file for resource type: {}", resourceType);

        try {
            Map<String, Object> result = importExportService.validateFileStructure(file, resourceType);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to validate file", e);
            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("valid", false);
            errorResponse.put("errors", List.of("Validation failed: " + e.getMessage()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Processes an import operation.
     *
     * @param file the uploaded file
     * @param resourceType the resource type
     * @param request HTTP request
     * @return import result
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResult> processImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam("resourceType") String resourceType,
            HttpServletRequest request) {

        String userId = (String) request.getAttribute(UserContextFilter.USER_ID_ATTR);
        log.info("Processing import for resource type: {} by user: {}", resourceType, userId);

        try {
            ImportResult result = importExportService.processImport(file, resourceType, null);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to process import", e);
            List<ImportError> errors = List.of(
                new ImportError(0, "import.importFailed", Map.of("message", e.getMessage()))
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ImportResult(0, 0, errors));
        }
    }

    /**
     * Exports all resources as an Excel file.
     *
     * @param request HTTP request
     * @return the Excel file
     */
    @GetMapping(value = "/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportResources(HttpServletRequest request) {

        String userId = (String) request.getAttribute(UserContextFilter.USER_ID_ATTR);
        log.info("Exporting resources for user: {}", userId);

        try {
            byte[] fileContent = importExportService.generateExportFile();

            String filename = "ops-resources-" + System.currentTimeMillis() + ".xlsx";

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(fileContent);
        } catch (Exception e) {
            log.error("Failed to export resources", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Downloads a sample template file for the specified resource type.
     *
     * @param resourceType the resource type
     * @param request HTTP request
     * @return the sample Excel file
     */
    @GetMapping(value = "/template", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> downloadTemplate(
            @RequestParam("resourceType") String resourceType,
            HttpServletRequest request) {

        log.info("Downloading template for resource type: {}", resourceType);

        try {
            // Placeholder: Generate sample template file
            // In actual implementation, this would create a proper Excel template

            byte[] fileContent = importExportService.generateExportFile();

            String filename = resourceType.toLowerCase() + "_template.xlsx";

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(fileContent);
        } catch (Exception e) {
            log.error("Failed to generate template", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
