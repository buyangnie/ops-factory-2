
package com.huawei.opsfactory.gateway.filter;

import com.huawei.opsfactory.gateway.service.OperationIntelligenceProxyService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.annotation.Order;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnBean(OperationIntelligenceProxyService.class)
@Order(10)
public class OperationIntelligenceProxyFilter implements jakarta.servlet.Filter {
    private static final String PREFIX = "/api/gateway/operation-intelligence/";

    private final OperationIntelligenceProxyService proxyService;

    public OperationIntelligenceProxyFilter(OperationIntelligenceProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @Override
    public void doFilter(jakarta.servlet.ServletRequest servletRequest, jakarta.servlet.ServletResponse servletResponse,
        FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        if (!request.getRequestURI().startsWith(PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        ResponseEntity<String> proxied = proxyService.proxy(request);
        response.setStatus(proxied.getStatusCode().value());

        HttpHeaders headers = proxied.getHeaders();
        String contentType = headers.getFirst(HttpHeaders.CONTENT_TYPE);
        if (contentType != null && !contentType.isBlank()) {
            response.setContentType(contentType);
        }
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        String body = proxied.getBody();
        if (body != null) {
            response.getWriter().write(body);
        }
    }
}
