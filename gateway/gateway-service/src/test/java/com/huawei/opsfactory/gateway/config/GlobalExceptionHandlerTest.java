/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

/**
 * Test coverage for Global Exception Handler.
 *
 * @author x00000000
 * @since 2026-05-26
 */
public class GlobalExceptionHandlerTest {
    private GlobalExceptionHandler handler;

    /**
     * Initializes the test fixture before each test method.
     */
    @Before
    public void setUp() {
        handler = new GlobalExceptionHandler();
    }

    /**
     * Tests that HTTP message not readable exceptions are handled correctly.
     * Verifies decoding errors return 400 status with appropriate error message.
     */
    @Test
    public void testHandleInputException_decodingError() {
        HttpMessageNotReadableException cause = new HttpMessageNotReadableException("bad json", null, null);
        ResponseEntity<Map<String, Object>> response = handler.handleInputException(cause);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull("Response body should not be null", body);
        assertFalse((Boolean) body.get("success"));
        assertEquals("bad json", body.get("error"));
    }

    /**
     * Tests that HTTP message not readable exceptions are handled correctly.
     * Verifies other input errors return 400 status with appropriate error message.
     */
    @Test
    public void testHandleInputException_otherError() {
        HttpMessageNotReadableException ex =
            new HttpMessageNotReadableException("Missing parameter 'name'", null, null);

        ResponseEntity<Map<String, Object>> response = handler.handleInputException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull("Response body should not be null", body);
        assertFalse((Boolean) body.get("success"));
        assertEquals("Missing parameter 'name'", body.get("error"));
    }

    /**
     * Tests that response status exceptions are handled correctly.
     * Verifies exceptions with reason return correct status and error message.
     */
    @Test
    public void testHandleResponseStatusException_withReason() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "session not found");

        ResponseEntity<Map<String, Object>> response = handler.handleResponseStatus(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull("Response body should not be null", body);
        assertFalse((Boolean) body.get("success"));
        assertEquals("session not found", body.get("error"));
    }

    /**
     * Tests that response status exceptions are handled correctly.
     * Verifies forbidden exceptions return 403 status with appropriate error message.
     */
    @Test
    public void testHandleResponseStatusException_forbidden() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.FORBIDDEN, "admin access required");

        ResponseEntity<Map<String, Object>> response = handler.handleResponseStatus(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull("Response body should not be null", body);
        assertFalse((Boolean) body.get("success"));
        assertEquals("admin access required", body.get("error"));
    }

    /**
     * Tests that response status exceptions are handled correctly.
     * Verifies exceptions without reason return correct status and fall back to message.
     */
    @Test
    public void testHandleResponseStatusException_noReason() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);

        ResponseEntity<Map<String, Object>> response = handler.handleResponseStatus(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull("Response body should not be null", body);
        assertFalse((Boolean) body.get("success"));
        // When no reason, falls back to getMessage()
        String error = (String) body.get("error");
        // getMessage() returns something like "500 INTERNAL_SERVER_ERROR"
        assertEquals(ex.getMessage(), error);
    }

    /**
     * Tests that no resource found exceptions are handled correctly.
     * Verifies 404 status is returned for missing resources.
     */
    @Test
    public void testHandleNoResourceFoundException() {
        NoResourceFoundException ex =
            new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "/missing/path");

        ResponseEntity<Map<String, Object>> response = handler.handleNoResourceFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    /**
     * Tests that unexpected exceptions are handled correctly.
     * Verifies 500 status is returned with generic error message.
     */
    @Test
    public void testHandleUnexpectedException() {
        Exception ex = new RuntimeException("Unexpected error");

        ResponseEntity<Map<String, Object>> response = handler.handleUnexpected(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull("Response body should not be null", body);
        assertFalse((Boolean) body.get("success"));
        assertEquals("Internal server error", body.get("error"));
    }
}