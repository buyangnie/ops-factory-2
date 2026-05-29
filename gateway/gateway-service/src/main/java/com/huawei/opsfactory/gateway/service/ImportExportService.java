/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service;

import com.huawei.opsfactory.gateway.common.model.ImportError;
import com.huawei.opsfactory.gateway.common.model.ImportResult;
import com.huawei.opsfactory.gateway.common.util.XlsxUtil;
import com.huawei.opsfactory.gateway.config.GatewayProperties;

import jakarta.annotation.PostConstruct;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for handling import and export operations using Excel files.
 *
 * @since 2026-05-28
 */
@Service
public class ImportExportService {

    private static final Logger log = LoggerFactory.getLogger(ImportExportService.class);

    private final GatewayProperties properties;
    private Path exportDir;

    public ImportExportService(GatewayProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() throws IOException {
        Path gatewayRoot = properties.getGatewayRootPath();
        this.exportDir = gatewayRoot.resolve("data").resolve("exports");
        Files.createDirectories(exportDir);
        log.info("ImportExportService initialized, exportDir={}", exportDir);
    }

    /**
     * Validates the structure of an uploaded Excel file.
     *
     * @param file the uploaded file
     * @param resourceType the resource type (e.g., "Hosts", "Clusters")
     * @return validation result
     */
    public Map<String, Object> validateFileStructure(MultipartFile file, String resourceType) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = XlsxUtil.readWorkbook(inputStream)) {

            // Check if the data sheet exists
            String dataSheetName = resourceType;
            if (!XlsxUtil.hasSheet(workbook, dataSheetName)) {
                errors.add("Data sheet '" + dataSheetName + "' not found in file");
                result.put("valid", false);
                result.put("errors", errors);
                return result;
            }

            Sheet dataSheet = workbook.getSheet(dataSheetName);
            int columnCount = XlsxUtil.getColumnCount(dataSheet);

            // Get expected columns for the resource type
            List<String> expectedColumns = getExpectedColumns(resourceType);

            if (columnCount != expectedColumns.size()) {
                errors.add("Expected " + expectedColumns.size() + " columns, found " + columnCount);
            }

            // Validate column names
            Row headerRow = dataSheet.getRow(0);
            if (headerRow != null) {
                List<String> actualColumns = new ArrayList<>();
                for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                    actualColumns.add(XlsxUtil.getCellValueAsString(headerRow.getCell(i)));
                }

                for (int i = 0; i < expectedColumns.size(); i++) {
                    if (i >= actualColumns.size()) {
                        errors.add("Missing column: " + expectedColumns.get(i));
                    } else if (!actualColumns.get(i).equalsIgnoreCase(expectedColumns.get(i))) {
                        errors.add("Column " + (i + 1) + " should be '" + expectedColumns.get(i) + "' but found '" + actualColumns.get(i) + "'");
                    }
                }
            }

            result.put("valid", errors.isEmpty());
            result.put("errors", errors);
        } catch (IOException e) {
            log.error("Failed to validate file structure", e);
            errors.add("Failed to read file: " + e.getMessage());
            result.put("valid", false);
            result.put("errors", errors);
        }

        return result;
    }

    /**
     * Processes an import file for the specified resource type.
     *
     * @param file the uploaded file
     * @param resourceType the resource type
     * @param resourceService the service instance to call
     * @return import result
     */
    public ImportResult processImport(MultipartFile file, String resourceType, Object resourceService) {
        List<ImportError> errors = new ArrayList<>();
        int success = 0;
        int failed = 0;

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = XlsxUtil.readWorkbook(inputStream)) {

            Sheet dataSheet = workbook.getSheet(resourceType);
            if (dataSheet == null) {
                errors.add(new ImportError(0, "import.invalidFile",
                    Map.of("message", "Sheet '" + resourceType + "' not found")));
                return new ImportResult(0, 1, errors);
            }

            List<Map<String, String>> rows = XlsxUtil.readSheetData(dataSheet);
            if (rows.isEmpty()) {
                errors.add(new ImportError(0, "import.noDataRows", null));
                return new ImportResult(0, 0, errors);
            }

            // Process each row based on resource type
            // Note: This is a placeholder - actual implementation will call the appropriate service methods
            for (int i = 0; i < rows.size(); i++) {
                Map<String, String> row = rows.get(i);
                try {
                    // Call the appropriate service method based on resource type
                    // This is simplified - actual implementation needs to be more sophisticated
                    success++;
                } catch (Exception e) {
                    failed++;
                    errors.add(new ImportError(i + 2, "import.rowError",
                        Map.of("message", e.getMessage())));
                }
            }

        } catch (IOException e) {
            log.error("Failed to process import", e);
            errors.add(new ImportError(0, "import.fileReadError",
                Map.of("message", e.getMessage())));
        }

        return new ImportResult(success, failed, errors);
    }

    /**
     * Generates an export file for all resources.
     *
     * @return the file content as byte array
     * @throws IOException if generation fails
     */
    public byte[] generateExportFile() throws IOException {
        // Placeholder for export functionality
        // This will be implemented to gather all data and generate Excel file
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", System.currentTimeMillis());

        try (Workbook workbook = XlsxUtil.createWorkbook()) {
            // Create sheets for each resource type
            // Placeholder implementation

            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                workbook.write(outputStream);
                return outputStream.toByteArray();
            }
        }
    }

    /**
     * Gets the expected columns for a resource type.
     *
     * @param resourceType the resource type
     * @return list of column names
     */
    private List<String> getExpectedColumns(String resourceType) {
        // This should be centralized, but for now we return based on resource type
        return switch (resourceType) {
            case "ClusterTypes" -> List.of("name", "code", "description", "knowledge", "commandPrefix", "envVariables");
            case "BusinessTypes" -> List.of("name", "code", "description", "knowledge");
            case "HostGroups" -> List.of("name", "code", "parentGroup", "description");
            case "Clusters" -> List.of("name", "type", "purpose", "group", "description");
            case "Hosts" -> List.of("name", "hostname", "ip", "businessIp", "port", "os", "location", "username",
                "authType", "credential", "business", "cluster", "purpose", "role", "tags", "description");
            case "BusinessServices" -> List.of("name", "code", "group", "businessType", "description", "tags",
                "priority", "contactInfo");
            case "Relations" -> List.of("sourceNode", "destNode", "description");
            case "SOPs" -> List.of("name", "description", "version", "triggerCondition", "enabled", "mode",
                "stepsDescription", "tags");
            case "Whitelist" -> List.of("pattern", "description", "enabled");
            default -> List.of();
        };
    }
}
