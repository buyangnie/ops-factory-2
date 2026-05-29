package com.huawei.opsfactory.finops.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.huawei.opsfactory.finops.model.FinOpsModels.SessionMessageRecord;
import com.huawei.opsfactory.finops.model.FinOpsModels.SessionUsageRecord;
import com.huawei.opsfactory.finops.model.FinOpsModels.UsageSnapshotPayload;
import com.huawei.opsfactory.finops.service.GatewayUsageSnapshotClient;
import com.huawei.opsfactory.finops.store.FinOpsSnapshotStore;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "finops.secret-key=test-secret",
        "finops.scan.refresh-interval-ms=3600000",
        "finops.scan.refresh-on-startup=false"
    }
)
class FinOpsControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private FinOpsSnapshotStore snapshotStore;

    @MockBean
    private GatewayUsageSnapshotClient snapshotClient;

    @Test
    void rejectsRequestsWithoutConfiguredSecret() {
        ResponseEntity<String> response = restTemplate.getForEntity("/finops/overview", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void exposesOverviewWithConfiguredSecret() {
        snapshotStore.update(new UsageSnapshotPayload(List.of(session("session-1")), List.of(), 1, 0, "test", null));

        ResponseEntity<String> response = restTemplate.exchange(
            "/finops/overview?compare=true",
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"snapshotStatus\"");
        assertThat(response.getBody()).contains("\"summary\"");
        assertThat(response.getBody()).contains("\"taskExecutionLoad\"");
        assertThat(response.getBody()).contains("\"topAgents\"");
        assertThat(response.getBody()).doesNotContain("\"recommendations\"");
        assertThat(response.getBody()).doesNotContain("workingDir");
        assertThat(response.getBody()).doesNotContain("threadId");
        assertThat(response.getBody()).doesNotContain("modelConfig");
        assertThat(response.getBody()).doesNotContain("recipe");
        assertThat(response.getBody()).doesNotContain("/tmp/internal-workdir");
    }

    @Test
    void exposesPaginatedListsWithPublicSessionFieldsOnly() {
        snapshotStore.update(new UsageSnapshotPayload(List.of(session("session-1"), session("session-2")), List.of(), 1, 0, "test", null));

        ResponseEntity<String> response = getWithSecret("/finops/sessions?page=1&size=1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"page\":1");
        assertThat(response.getBody()).contains("\"size\":1");
        assertThat(response.getBody()).contains("\"totalItems\":2");
        assertThat(response.getBody()).contains("\"totalPages\":2");
        assertThat(response.getBody()).contains("\"label\"");
        assertThat(response.getBody()).doesNotContain("workingDir");
        assertThat(response.getBody()).doesNotContain("threadId");
        assertThat(response.getBody()).doesNotContain("modelConfig");
        assertThat(response.getBody()).doesNotContain("recipe");
    }

    @Test
    void exposesSessionDetailByUserAgentScopedIdentity() {
        snapshotStore.update(new UsageSnapshotPayload(
            List.of(
                session("shared-session", "admin", "qa-agent", "Admin scoped session"),
                session("shared-session", "alice", "kb-agent", "Alice scoped session")
            ),
            List.of(),
            1,
            0,
            "test",
            null
        ));

        ResponseEntity<String> response = getWithSecret("/finops/sessions/shared-session?userId=alice&agentId=kb-agent");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"userId\":\"alice\"");
        assertThat(response.getBody()).contains("\"agentId\":\"kb-agent\"");
        assertThat(response.getBody()).contains("Alice scoped session");
        assertThat(response.getBody()).doesNotContain("Admin scoped session");
    }

    @Test
    void rejectsMalformedTimestampAsBadRequest() {
        ResponseEntity<String> response = getWithSecret("/finops/overview?startTime=bad-time");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("FINOPS_INVALID_REQUEST");
    }

    @Test
    void exposesSessionMessagesForUserAgentScopedSession() {
        Instant now = Instant.now();
        snapshotStore.update(new UsageSnapshotPayload(
            List.of(session("session-1")),
            List.of(new SessionMessageRecord(
                "session-1",
                "admin",
                "qa-agent",
                "message-1",
                1,
                "user",
                now,
                now,
                null,
                24,
                "Open the large report",
                "Open the large report",
                false,
                false,
                true,
                "read_file",
                false,
                true,
                true
            )),
            1,
            0,
            "test",
            null
        ));

        ResponseEntity<String> response = getWithSecret("/finops/sessions/session-1/messages?userId=admin&agentId=qa-agent");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"session\"");
        assertThat(response.getBody()).contains("\"messages\"");
        assertThat(response.getBody()).contains("\"messageTokenAvailable\":false");
        assertThat(response.getBody()).contains("\"toolSignalAvailable\":true");
        assertThat(response.getBody()).contains("\"toolResponse\":true");
        assertThat(response.getBody()).contains("Open the large report");
        assertThat(response.getBody()).doesNotContain("workingDir");
        assertThat(response.getBody()).doesNotContain("threadId");
    }

    @Test
    void refreshLoadsGatewaySnapshotAndMakesItAvailableToOverview() {
        when(snapshotClient.fetchSnapshot()).thenReturn(new UsageSnapshotPayload(
            List.of(session("gateway-session")),
            List.of(),
            1,
            0,
            "gateway",
            null
        ));

        ResponseEntity<String> refresh = restTemplate.exchange(
            "/finops/refresh",
            HttpMethod.POST,
            new HttpEntity<>(headers()),
            String.class
        );
        ResponseEntity<String> overview = getWithSecret("/finops/overview?compare=true");

        assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refresh.getBody()).contains("\"status\":\"ready\"");
        assertThat(refresh.getBody()).contains("\"sessionCount\":1");
        assertThat(overview.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(overview.getBody()).contains("gateway-session");
        assertThat(overview.getBody()).contains("\"totalTokens\":1000");
    }

    @Test
    void removedRecommendationAndReportEndpointsStayUnavailable() {
        assertThat(getWithSecret("/finops/recommendations").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(getWithSecret("/finops/reports/summary").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<String> getWithSecret(String path) {
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers()), String.class);
    }

    private static HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-secret-key", "test-secret");
        return headers;
    }

    private static SessionUsageRecord session(String id) {
        return session(id, "admin", "qa-agent", "Session " + id);
    }

    private static SessionUsageRecord session(String id, String userId, String agentId, String label) {
        Instant updatedAt = Instant.now().minusSeconds(60);
        return new SessionUsageRecord(
            id,
            userId,
            agentId,
            label,
            "user",
            "/tmp/internal-workdir",
            updatedAt.minusSeconds(3600),
            updatedAt,
            1000,
            900,
            100,
            1000,
            900,
            100,
            null,
            "custom_provider",
            "qwen/test",
            "auto",
            "thread-" + id,
            3,
            1,
            1,
            1,
            label,
            Map.of("secret", "raw-model-config"),
            Map.of("prompt", "raw-recipe")
        );
    }
}
