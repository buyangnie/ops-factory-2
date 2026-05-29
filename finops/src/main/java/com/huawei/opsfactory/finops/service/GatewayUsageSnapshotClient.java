package com.huawei.opsfactory.finops.service;

import com.huawei.opsfactory.finops.config.FinOpsProperties;
import com.huawei.opsfactory.finops.model.FinOpsModels.UsageSnapshotPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Reads FinOps usage snapshots from the gateway usage API.
 *
 * @since 2026-05-28
 */
@Service
public class GatewayUsageSnapshotClient {

    private static final String SECRET_HEADER = "x-secret-key";
    private static final String USER_HEADER = "x-user-id";
    private static final String SNAPSHOT_PATH = "/gateway/usage/session-snapshot";

    private final FinOpsProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /**
     * Creates a gateway usage snapshot client.
     *
     * @param properties FinOps configuration properties
     * @param objectMapper JSON object mapper
     */
    public GatewayUsageSnapshotClient(FinOpsProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Fetches the latest gateway-owned session usage snapshot.
     *
     * @return normalized usage snapshot
     */
    public UsageSnapshotPayload fetchSnapshot() {
        HttpRequest request = buildRequest();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Gateway FinOps snapshot request failed with HTTP " + response.statusCode());
            }
            return objectMapper.readValue(response.body(), UsageSnapshotPayload.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Gateway FinOps snapshot response is not valid JSON", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Gateway FinOps snapshot request failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gateway FinOps snapshot request was interrupted", ex);
        }
    }

    private HttpRequest buildRequest() {
        FinOpsProperties.Gateway gateway = properties.getGateway();
        HttpRequest.Builder builder = HttpRequest.newBuilder(snapshotUri(gateway.getBaseUrl()))
            .timeout(Duration.ofMillis(Math.max(1, gateway.getTimeoutMs())))
            .header(HttpHeaders.ACCEPT, "application/json")
            .GET();
        String secretKey = gateway.getSecretKey();
        if (secretKey != null && !secretKey.isBlank()) {
            builder.header(SECRET_HEADER, secretKey);
        }
        String userId = gateway.getUserId();
        if (userId != null && !userId.isBlank()) {
            builder.header(USER_HEADER, userId);
        }
        return builder.build();
    }

    private URI snapshotUri(String baseUrl) {
        String normalized = (baseUrl == null || baseUrl.isBlank() ? "http://127.0.0.1:3000" : baseUrl.trim());
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return URI.create(normalized + SNAPSHOT_PATH);
    }
}
