package com.huawei.opsfactory.finops.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

/**
 * Converts FinOps service exceptions into stable API error responses.
 *
 * @since 2026-05-28
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * Handles invalid FinOps request parameters.
     *
     * @param ex invalid request exception
     * @return error response
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
            "code", "FINOPS_INVALID_REQUEST",
            "message", ex.getMessage()
        ));
    }

    /**
     * Handles FinOps service state failures.
     *
     * @param ex service state exception
     * @return error response
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleState(IllegalStateException ex) {
        log.error("FinOps request failed", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
            "code", "FINOPS_SCAN_FAILED",
            "message", ex.getMessage()
        ));
    }

    /**
     * Handles missing static or API resources.
     *
     * @param ex missing resource exception
     * @return error response
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
            "code", "FINOPS_NOT_FOUND",
            "message", ex.getMessage()
        ));
    }

}
