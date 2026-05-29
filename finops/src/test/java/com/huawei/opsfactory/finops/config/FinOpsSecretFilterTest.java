package com.huawei.opsfactory.finops.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class FinOpsSecretFilterTest {

    @Test
    void acceptsRequestWithConfiguredSecret() throws Exception {
        FilterResult result = filter("expected", "expected", "GET", "/finops/overview");

        assertThat(result.response().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(result.chain().getRequest()).isNotNull();
    }

    @Test
    void rejectsRequestWithInvalidSecret() throws Exception {
        FilterResult result = filter("expected", "wrong", "GET", "/finops/overview");

        assertThat(result.response().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(result.chain().getRequest()).isNull();
    }

    @Test
    void skipsAuthenticationWhenSecretIsBlank() throws Exception {
        FilterResult result = filter("", null, "GET", "/finops/overview");

        assertThat(result.response().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(result.chain().getRequest()).isNotNull();
    }

    @Test
    void allowsOptionsPreflightWithoutSecret() throws Exception {
        FilterResult result = filter("expected", null, "OPTIONS", "/finops/overview");

        assertThat(result.response().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(result.chain().getRequest()).isNotNull();
    }

    private static FilterResult filter(String configuredSecret,
                                       String requestSecret,
                                       String method,
                                       String path) throws ServletException, IOException {
        FinOpsProperties properties = new FinOpsProperties();
        properties.setSecretKey(configuredSecret);
        FinOpsSecretFilter filter = new FinOpsSecretFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (requestSecret != null) {
            request.addHeader("x-secret-key", requestSecret);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);
        return new FilterResult(response, chain);
    }

    private record FilterResult(MockHttpServletResponse response, MockFilterChain chain) {
    }
}
