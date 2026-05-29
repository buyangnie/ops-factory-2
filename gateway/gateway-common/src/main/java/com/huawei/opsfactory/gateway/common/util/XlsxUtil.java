/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.common.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for Excel file operations using Apache POI.
 *
 * @since 2026-05-28
 */
public class XlsxUtil {

    private static final Logger log = LoggerFactory.getLogger(XlsxUtil.class);


    /**
     * Reads an Excel file from input stream.
     *
     * @param inputStream the input stream
     * @return the workbook
     * @throws IOException if reading fails
     */
    public static Workbook readWorkbook(InputStream inputStream) throws IOException {
        return WorkbookFactory.create(inputStream);
    }

    /**
     * Creates a new empty workbook.
     *
     * @return the new workbook
     */
    public static Workbook createWorkbook() {
        return new XSSFWorkbook();
    }

    /**
     * Writes a workbook to byte array.
     *
     * @param workbook the workbook to write
     * @return the byte array
     * @throws IOException if writing fails
     */
    public static byte[] writeWorkbook(Workbook workbook) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    /**
     * Gets a sheet by name, creating it if it doesn't exist.
     *
     * @param workbook the workbook
     * @param sheetName the sheet name
     * @return the sheet
     */
    public static Sheet getOrCreateSheet(Workbook workbook, String sheetName) {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            sheet = workbook.createSheet(sheetName);
        }
        return sheet;
    }

    /**
     * Reads all data from a sheet as list of maps.
     *
     * @param sheet the sheet to read
     * @return list of row data maps (column name -> value)
     */
    public static List<Map<String, String>> readSheetData(Sheet sheet) {
        List<Map<String, String>> result = new ArrayList<>();

        if (sheet.getPhysicalNumberOfRows() < 1) {
            return result;
        }

        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            return result;
        }

        List<String> headers = new ArrayList<>();
        for (Cell cell : headerRow) {
            headers.add(getCellValueAsString(cell));
        }

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }

            Map<String, String> rowData = new HashMap<>();
            for (int j = 0; j < headers.size(); j++) {
                Cell cell = row.getCell(j);
                String value = cell != null ? getCellValueAsString(cell) : "";
                rowData.put(headers.get(j), value);
            }
            result.add(rowData);
        }

        return result;
    }

    /**
     * Gets cell value as string.
     *
     * @param cell the cell
     * @return the cell value as string
     */
    public static String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        CellType cellType = cell.getCellType();
        if (cellType == null) {
            return "";
        }

        switch (cellType) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            case BLANK:
                return "";
            default:
                return "";
        }
    }

    /**
     * Gets the number of columns in a sheet.
     *
     * @param sheet the sheet
     * @return the number of columns
     */
    public static int getColumnCount(Sheet sheet) {
        if (sheet.getPhysicalNumberOfRows() == 0) {
            return 0;
        }
        Row firstRow = sheet.getRow(0);
        return firstRow != null ? firstRow.getLastCellNum() : 0;
    }

    /**
     * Checks if a sheet exists in the workbook.
     *
     * @param workbook the workbook
     * @param sheetName the sheet name
     * @return true if the sheet exists
     */
    public static boolean hasSheet(Workbook workbook, String sheetName) {
        return workbook.getSheet(sheetName) != null;
    }

    /**
     * Closes a workbook quietly.
     *
     * @param workbook the workbook to close
     */
    public static void closeQuietly(Workbook workbook) {
        if (workbook != null) {
            try {
                workbook.close();
            } catch (IOException e) {
                if (log.isDebugEnabled()) {
                    log.debug("Failed to close workbook: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Closes an input stream quietly.
     *
     * @param inputStream the input stream to close
     */
    public static void closeQuietly(InputStream inputStream) {
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException e) {
                if (log.isDebugEnabled()) {
                    log.debug("Failed to close input stream: {}", e.getMessage());
                }
            }
        }
    }
}
