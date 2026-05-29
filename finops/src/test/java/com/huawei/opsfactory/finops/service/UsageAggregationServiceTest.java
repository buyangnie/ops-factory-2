package com.huawei.opsfactory.finops.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.huawei.opsfactory.finops.model.FinOpsModels.AgentUsage;
import com.huawei.opsfactory.finops.model.FinOpsModels.ModelUsage;
import com.huawei.opsfactory.finops.model.FinOpsModels.QueryFilter;
import com.huawei.opsfactory.finops.model.FinOpsModels.SessionUsageRecord;
import com.huawei.opsfactory.finops.model.FinOpsModels.TaskExecutionLoad;
import com.huawei.opsfactory.finops.model.FinOpsModels.UsageFilterRequest;
import com.huawei.opsfactory.finops.model.FinOpsModels.UsageSummary;
import com.huawei.opsfactory.finops.model.FinOpsModels.UserUsage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UsageAggregationServiceTest {

    private final UsageAggregationService service = new UsageAggregationService();

    @Test
    void aggregatesUsageByCoreFinOpsDimensions() {
        List<SessionUsageRecord> sessions = List.of(
            session("s1", "admin", "qa-agent", "manual", null, "custom-qwen", "qwen/qwen3.5-27b",
                "2026-05-20T10:00:00Z", 1000, 900, 100),
            session("s2", "admin", "qa-agent", "scheduled", "schedule-1", "custom-qwen", "qwen/qwen3.5-27b",
                "2026-05-21T10:00:00Z", 2000, 1800, 200),
            session("s3", "alice", "ops-agent", "manual", null, "ollama", "qwen3.5:9b",
                "2026-05-22T10:00:00Z", 500, 400, 100)
        );

        UsageSummary summary = service.summarize(sessions);
        List<AgentUsage> agents = service.agents(sessions);
        List<UserUsage> users = service.users(sessions);
        List<ModelUsage> models = service.models(sessions);
        TaskExecutionLoad taskExecutionLoad = service.taskExecutionLoad(sessions);

        assertThat(summary.sessionCount()).isEqualTo(3);
        assertThat(summary.totalTokens()).isEqualTo(3500);
        assertThat(summary.inputTokens()).isEqualTo(3100);
        assertThat(summary.outputTokens()).isEqualTo(400);
        assertThat(summary.activeUsers()).isEqualTo(2);
        assertThat(summary.activeAgents()).isEqualTo(2);
        assertThat(summary.activeModels()).isEqualTo(2);
        assertThat(summary.scheduledSessionCount()).isEqualTo(1);
        assertThat(summary.manualSessionCount()).isEqualTo(2);
        assertThat(summary.avgTokensPerSession()).isEqualTo(3500.0 / 3);

        assertThat(agents).extracting(AgentUsage::agentId).containsExactly("qa-agent", "ops-agent");
        assertThat(agents.get(0).totalTokens()).isEqualTo(3000);
        assertThat(agents.get(0).scheduledSessionCount()).isEqualTo(1);
        assertThat(agents.get(0).highTokenSessionCount()).isGreaterThanOrEqualTo(1);

        assertThat(users).extracting(UserUsage::userId).containsExactly("admin", "alice");
        assertThat(users.get(0).topAgent()).isEqualTo("qa-agent");

        assertThat(models).extracting(ModelUsage::providerName).containsExactly("custom-qwen", "ollama");
        assertThat(models.get(0).activeAgents()).isEqualTo(1);

        assertThat(taskExecutionLoad.avgTokensPerTask()).isEqualTo(3500.0 / 3);
        assertThat(taskExecutionLoad.avgMessagesPerTask()).isEqualTo(3);
        assertThat(taskExecutionLoad.avgToolResponsesPerTask()).isEqualTo(1);
    }

    @Test
    void filtersCurrentAndPreviousEqualWindows() {
        List<SessionUsageRecord> sessions = List.of(
            session("previous", "admin", "agent-a", "manual", null, "provider-a", "model-a",
                "2026-04-20T10:00:00Z", 100, 90, 10),
            session("current", "admin", "agent-a", "manual", null, "provider-a", "model-a",
                "2026-05-20T10:00:00Z", 300, 250, 50),
            session("outside", "admin", "agent-b", "manual", null, "provider-a", "model-a",
                "2026-03-01T10:00:00Z", 999, 900, 99)
        );
        QueryFilter filter = service.buildFilter(new UsageFilterRequest(
            "2026-05-01T00:00:00Z",
            "2026-06-01T00:00:00Z",
            "agent-a",
            null,
            null,
            null,
            null,
            true
        ));

        assertThat(service.filterCurrent(sessions, filter)).extracting(SessionUsageRecord::id).containsExactly("current");
        assertThat(service.filterPrevious(sessions, filter)).extracting(SessionUsageRecord::id).containsExactly("previous");
        assertThat(service.comparison(service.filterCurrent(sessions, filter), service.filterPrevious(sessions, filter)).tokenGrowthRate())
            .isEqualTo(2.0);
    }

    @Test
    void rejectsInvalidTimeRange() {
        assertThatThrownBy(() -> service.buildFilter(new UsageFilterRequest(
            "2026-06-01T00:00:00Z",
            "2026-05-01T00:00:00Z",
            null,
            null,
            null,
            null,
            null,
            false
        ))).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("startTime must be before endTime");
    }

    private static SessionUsageRecord session(String id,
                                              String userId,
                                              String agentId,
                                              String sessionType,
                                              String scheduleId,
                                              String providerName,
                                              String modelName,
                                              String updatedAt,
                                              long totalTokens,
                                              long inputTokens,
                                              long outputTokens) {
        Instant updated = Instant.parse(updatedAt);
        return new SessionUsageRecord(
            id,
            userId,
            agentId,
            "Session " + id,
            sessionType,
            "/work",
            updated.minusSeconds(3600),
            updated,
            totalTokens,
            inputTokens,
            outputTokens,
            totalTokens,
            inputTokens,
            outputTokens,
            scheduleId,
            providerName,
            modelName,
            "auto",
            "thread-" + id,
            3,
            1,
            1,
            1,
            "Session " + id,
            Map.of("model", modelName),
            Map.of()
        );
    }
}
