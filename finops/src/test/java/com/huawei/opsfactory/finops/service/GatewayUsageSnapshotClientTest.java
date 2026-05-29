package com.huawei.opsfactory.finops.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.opsfactory.finops.config.FinOpsProperties;
import com.huawei.opsfactory.finops.model.FinOpsModels.UsageSnapshotPayload;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GatewayUsageSnapshotClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void fetchesSnapshotFromGatewayUsageEndpointWithGatewayHeaders() throws IOException {
        List<String> observedSecrets = new ArrayList<>();
        List<String> observedUsers = new ArrayList<>();
        AtomicInteger requestCount = new AtomicInteger();
        startServer(exchange -> {
            requestCount.incrementAndGet();
            observedSecrets.add(exchange.getRequestHeaders().getFirst("x-secret-key"));
            observedUsers.add(exchange.getRequestHeaders().getFirst("x-user-id"));
            send(exchange, 200, """
                {
                  "sessions": [
                    {
                      "id": "session-1",
                      "userId": "admin",
                      "agentId": "qa-agent",
                      "name": "Session 1",
                      "sessionType": "user",
                      "workingDir": "/tmp/work",
                      "createdAt": "2026-05-27T10:00:00Z",
                      "updatedAt": "2026-05-27T10:05:00Z",
                      "totalTokens": 1200,
                      "inputTokens": 1000,
                      "outputTokens": 200,
                      "accumulatedTotalTokens": 1200,
                      "accumulatedInputTokens": 1000,
                      "accumulatedOutputTokens": 200,
                      "scheduleId": null,
                      "providerName": "custom-qwen",
                      "modelName": "qwen/qwen3.5-27b",
                      "gooseMode": "auto",
                      "threadId": "thread-1",
                      "messageCount": 3,
                      "userMessageCount": 1,
                      "assistantMessageCount": 1,
                      "toolResponseCount": 1,
                      "label": "Session 1",
                      "modelConfig": {},
                      "recipe": {}
                    }
                  ],
                  "messages": [],
                  "sourceDbCount": 1,
                  "skippedDbCount": 0,
                  "dataSource": "gateway",
                  "lastError": null
                }
                """);
        });

        UsageSnapshotPayload result = client("gateway-secret").fetchSnapshot();

        assertThat(requestCount).hasValue(1);
        assertThat(observedSecrets).containsExactly("gateway-secret");
        assertThat(observedUsers).containsExactly("admin");
        assertThat(result.sessions()).hasSize(1);
        assertThat(result.sessions().get(0).id()).isEqualTo("session-1");
        assertThat(result.dataSource()).isEqualTo("gateway");
    }

    @Test
    void omitsSecretHeaderWhenGatewaySecretIsBlank() throws IOException {
        List<String> observedSecrets = new ArrayList<>();
        startServer(exchange -> {
            observedSecrets.add(exchange.getRequestHeaders().getFirst("x-secret-key"));
            send(exchange, 200, """
                {"sessions":[],"messages":[],"sourceDbCount":0,"skippedDbCount":0,"dataSource":"gateway","lastError":null}
                """);
        });

        UsageSnapshotPayload result = client("").fetchSnapshot();

        assertThat(result.sessions()).isEmpty();
        assertThat(observedSecrets).containsExactly((String) null);
    }

    @Test
    void omitsUserHeaderWhenGatewayUserIsBlank() throws IOException {
        List<String> observedUsers = new ArrayList<>();
        startServer(exchange -> {
            observedUsers.add(exchange.getRequestHeaders().getFirst("x-user-id"));
            send(exchange, 200, """
                {"sessions":[],"messages":[],"sourceDbCount":0,"skippedDbCount":0,"dataSource":"gateway","lastError":null}
                """);
        });

        UsageSnapshotPayload result = client("gateway-secret", "").fetchSnapshot();

        assertThat(result.sessions()).isEmpty();
        assertThat(observedUsers).containsExactly((String) null);
    }

    @Test
    void reportsNonSuccessGatewayResponse() throws IOException {
        startServer(exchange -> send(exchange, 503, "{\"error\":\"unavailable\"}"));

        assertThatThrownBy(() -> client("gateway-secret").fetchSnapshot())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("HTTP 503");
    }

    private GatewayUsageSnapshotClient client(String secretKey) {
        return client(secretKey, "admin");
    }

    private GatewayUsageSnapshotClient client(String secretKey, String userId) {
        FinOpsProperties properties = new FinOpsProperties();
        properties.getGateway().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        properties.getGateway().setSecretKey(secretKey);
        properties.getGateway().setUserId(userId);
        properties.getGateway().setTimeoutMs(5000);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        return new GatewayUsageSnapshotClient(properties, objectMapper);
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/gateway/usage/session-snapshot", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                send(exchange, 405, "{}");
                return;
            }
            handler.handle(exchange);
        });
        server.start();
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("content-type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
