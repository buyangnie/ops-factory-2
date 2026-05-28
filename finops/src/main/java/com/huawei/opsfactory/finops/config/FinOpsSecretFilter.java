package com.huawei.opsfactory.finops.config;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Enforces the optional FinOps shared-secret header.
 *
 * @since 2026-05-28
 */
@Component
public class FinOpsSecretFilter extends OncePerRequestFilter {

    private static final String SECRET_HEADER = "x-secret-key";
    private final FinOpsProperties properties;

    /**
     * Creates the optional shared-secret filter.
     *
     * @param properties FinOps configuration properties
     */
    public FinOpsSecretFilter(FinOpsProperties properties) {
        this.properties = properties;
    }

    /**
     * Skips filtering for actuator endpoints.
     *
     * @param request HTTP request
     * @return true when the request should bypass this filter
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/");
    }

    /**
     * Validates the configured shared-secret header when FinOps auth is enabled.
     *
     * @param request HTTP request
     * @param response HTTP response
     * @param filterChain servlet filter chain
     * @throws ServletException when downstream filtering fails
     * @throws IOException when downstream I/O fails
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        String expected = properties.getSecretKey();
        if (expected != null && !expected.isBlank() && !matchesSecret(expected, request.getHeader(SECRET_HEADER))) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean matchesSecret(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}
