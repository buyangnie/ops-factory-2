/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Operation intelligence REST exception handler.
 *
 * @author x00000000
 * @since 2026-05-22
 */
@RestControllerAdvice
public class OperationIntelligenceExceptionHandler {
    /**
     * Handles response status exceptions.
     *
     * @param exception the exception
     * @return error response
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException exception) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("result", null);
        body.put("error", exception.getReason());
        return ResponseEntity.status(exception.getStatusCode()).body(body);
    }
}
