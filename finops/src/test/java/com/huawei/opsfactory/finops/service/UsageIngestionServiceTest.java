package com.huawei.opsfactory.finops.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.huawei.opsfactory.finops.config.FinOpsProperties;
import com.huawei.opsfactory.finops.model.FinOpsModels.SessionUsageRecord;
import com.huawei.opsfactory.finops.model.FinOpsModels.UsageSnapshotPayload;
import com.huawei.opsfactory.finops.store.FinOpsSnapshotStore;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UsageIngestionServiceTest {

    @Test
    void refreshStoresGatewaySnapshot() {
        GatewayUsageSnapshotClient client = mock(GatewayUsageSnapshotClient.class);
        FinOpsSnapshotStore store = new FinOpsSnapshotStore();
        when(client.fetchSnapshot()).thenReturn(payload("session-1", 1, 0, null));
        UsageIngestionService service = new UsageIngestionService(new FinOpsProperties(), client, store);

        FinOpsSnapshotStore.Snapshot result = service.refresh();

        assertThat(result.status().status()).isEqualTo("ready");
        assertThat(result.status().sessionCount()).isEqualTo(1);
        assertThat(result.status().sourceDbCount()).isEqualTo(1);
        assertThat(store.current().session("session-1", "admin", "qa-agent")).isNotNull();
    }

    @Test
    void refreshPreservesPreviousSnapshotWhenGatewayFails() {
        GatewayUsageSnapshotClient client = mock(GatewayUsageSnapshotClient.class);
        FinOpsSnapshotStore store = new FinOpsSnapshotStore();
        store.update(payload("previous-session", 1, 0, null));
        when(client.fetchSnapshot()).thenThrow(new IllegalStateException("gateway unavailable"));
        UsageIngestionService service = new UsageIngestionService(new FinOpsProperties(), client, store);

        FinOpsSnapshotStore.Snapshot result = service.refresh();

        assertThat(result.status().status()).isEqualTo("stale");
        assertThat(result.status().lastRefreshError()).isEqualTo("gateway unavailable");
        assertThat(result.status().sessionCount()).isEqualTo(1);
        assertThat(result.session("previous-session", "admin", "qa-agent")).isNotNull();
    }

    @Test
    void refreshMarksSnapshotPartialWhenGatewayReportsSkippedSources() {
        GatewayUsageSnapshotClient client = mock(GatewayUsageSnapshotClient.class);
        FinOpsSnapshotStore store = new FinOpsSnapshotStore();
        when(client.fetchSnapshot()).thenReturn(payload("partial-session", 2, 1, "one database failed"));
        UsageIngestionService service = new UsageIngestionService(new FinOpsProperties(), client, store);

        FinOpsSnapshotStore.Snapshot result = service.refresh();

        assertThat(result.status().status()).isEqualTo("partial");
        assertThat(result.status().sourceDbCount()).isEqualTo(2);
        assertThat(result.status().skippedDbCount()).isEqualTo(1);
        assertThat(result.status().lastRefreshError()).isEqualTo("one database failed");
    }

    private static UsageSnapshotPayload payload(String sessionId, int sourceDbCount, int skippedDbCount, String lastError) {
        return new UsageSnapshotPayload(
            List.of(session(sessionId)),
            List.of(),
            sourceDbCount,
            skippedDbCount,
            "gateway",
            lastError
        );
    }

    private static SessionUsageRecord session(String id) {
        Instant updatedAt = Instant.parse("2026-05-27T10:00:00Z");
        return new SessionUsageRecord(
            id,
            "admin",
            "qa-agent",
            "Session " + id,
            "user",
            "/tmp/work",
            updatedAt.minusSeconds(60),
            updatedAt,
            1200,
            1000,
            200,
            1200,
            1000,
            200,
            null,
            "custom-qwen",
            "qwen/qwen3.5-27b",
            "auto",
            "thread-" + id,
            3,
            1,
            1,
            1,
            "Session " + id,
            Map.of(),
            Map.of()
        );
    }
}
