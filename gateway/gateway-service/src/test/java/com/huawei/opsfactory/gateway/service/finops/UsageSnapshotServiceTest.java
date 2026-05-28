package com.huawei.opsfactory.gateway.service.finops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.opsfactory.gateway.model.finops.UsageSnapshotModels.SessionUsageRecord;
import com.huawei.opsfactory.gateway.model.finops.UsageSnapshotModels.SnapshotPayload;
import com.huawei.opsfactory.gateway.service.AgentConfigService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UsageSnapshotServiceTest {

    @TempDir
    private Path tempDir;

    @Test
    void scansGoosedSessionDatabasesWithUserAgentAndMessageMetadata() throws Exception {
        Path db = tempDir.resolve("admin/agents/qa-agent/data/sessions/sessions.db");
        Files.createDirectories(db.getParent());
        createFixtureDb(db);

        AgentConfigService agentConfigService = mock(AgentConfigService.class);
        when(agentConfigService.getUsersDir()).thenReturn(tempDir);
        UsageSnapshotService snapshotService = new UsageSnapshotService(agentConfigService, new ObjectMapper());

        SnapshotPayload result = snapshotService.snapshot();
        List<SessionUsageRecord> sessions = result.sessions().stream()
            .sorted(Comparator.comparing(SessionUsageRecord::id))
            .toList();

        assertThat(result.sourceDbCount()).isEqualTo(1);
        assertThat(result.skippedDbCount()).isZero();
        assertThat(sessions).hasSize(2);
        assertThat(result.messages()).hasSize(5);
        assertThat(result.messages().get(0).contentPreview()).isEqualTo("Analyze incident impact");
        assertThat(result.messages().get(2).toolResponse()).isTrue();
        assertThat(result.messages().get(2).toolName()).isEqualTo("search");
        assertThat(result.messages().get(3).toolResponse()).isTrue();
        assertThat(result.messages().get(3).toolName()).isEqualTo("call_123");
        assertThat(result.messages().get(4).toolResponse()).isTrue();
        assertThat(result.messages().get(4).toolName()).isEqualTo("fetch");

        SessionUsageRecord manual = sessions.get(0);
        assertThat(manual.id()).isEqualTo("manual-1");
        assertThat(manual.userId()).isEqualTo("admin");
        assertThat(manual.agentId()).isEqualTo("qa-agent");
        assertThat(manual.sessionType()).isEqualTo("manual");
        assertThat(manual.label()).isEqualTo("Analyze incident impact");
        assertThat(manual.providerName()).isEqualTo("custom_qwen");
        assertThat(manual.modelName()).isEqualTo("qwen/qwen3.5-27b");
        assertThat(manual.totalTokens()).isEqualTo(1200);
        assertThat(manual.messageCount()).isEqualTo(5);
        assertThat(manual.userMessageCount()).isEqualTo(1);
        assertThat(manual.assistantMessageCount()).isEqualTo(4);
        assertThat(manual.toolResponseCount()).isEqualTo(3);

        SessionUsageRecord scheduled = sessions.get(1);
        assertThat(scheduled.id()).isEqualTo("scheduled-1");
        assertThat(scheduled.sessionType()).isEqualTo("scheduled");
        assertThat(scheduled.scheduleId()).isEqualTo("schedule-1");
        assertThat(scheduled.label()).isEqualTo("Daily report - Summarize operations");
        assertThat(scheduled.modelName()).isEqualTo("qwen3.5:9b");
    }

    @Test
    void marksDbSkippedWhenMessageQueryFails() throws Exception {
        Path db = tempDir.resolve("admin/agents/qa-agent/data/sessions/sessions.db");
        Files.createDirectories(db.getParent());
        createDbWithBrokenMessageSchema(db);

        AgentConfigService agentConfigService = mock(AgentConfigService.class);
        when(agentConfigService.getUsersDir()).thenReturn(tempDir);
        UsageSnapshotService snapshotService = new UsageSnapshotService(agentConfigService, new ObjectMapper());

        SnapshotPayload result = snapshotService.snapshot();

        assertThat(result.sourceDbCount()).isEqualTo(1);
        assertThat(result.skippedDbCount()).isEqualTo(1);
        assertThat(result.sessions()).isEmpty();
        assertThat(result.messages()).isEmpty();
        assertThat(result.lastError()).contains("sessions.db");
    }

    private static void createFixtureDb(Path db) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db)) {
            try (var statement = connection.createStatement()) {
                statement.execute("""
                    create table sessions (
                        id text primary key,
                        name text,
                        session_type text,
                        working_dir text,
                        created_at text,
                        updated_at text,
                        total_tokens integer,
                        input_tokens integer,
                        output_tokens integer,
                        accumulated_total_tokens integer,
                        accumulated_input_tokens integer,
                        accumulated_output_tokens integer,
                        schedule_id text,
                        recipe_json text,
                        provider_name text,
                        model_config_json text,
                        goose_mode text,
                        thread_id text
                    )
                    """);
            }
            try (var statement = connection.createStatement()) {
                statement.execute("""
                    create table messages (
                        id integer primary key,
                        message_id text,
                        session_id text,
                        role text,
                        content_json text,
                        created_timestamp integer,
                        timestamp integer,
                        tokens integer,
                        metadata_json text
                    )
                    """);
            }
            insertSession(connection, "manual-1", "New Chat", null, null, null,
                "custom_qwen", "{\"model\":\"qwen/qwen3.5-27b\"}", 1200, 1000, 200);
            insertSession(connection, "scheduled-1", null, null, "schedule-1",
                "{\"title\":\"Daily report\",\"description\":\"Summarize operations\"}",
                "custom_ollama", "{\"model_name\":\"qwen3.5:9b\"}", 300, 250, 50);
            insertMessage(connection, "m1", "manual-1", "user", "{\"type\":\"text\",\"text\":\"Analyze incident impact\"}", 1);
            insertMessage(connection, "m2", "manual-1", "assistant", "{\"type\":\"text\",\"text\":\"Working\"}", 2);
            insertMessage(connection, "m3", "manual-1", "assistant", "{\"toolResponse\":{\"name\":\"search\",\"ok\":true}}", 3);
            insertMessage(connection, "m4", "manual-1", "assistant", "\"toolResponse call_123 success {\\\"hits\\\":[1]}\"", 4);
            insertMessage(connection, "m5", "manual-1", "assistant", "{\"type\":\"tool_result\",\"name\":\"fetch\",\"content\":\"done\"}", 5);
        }
    }

    private static void createDbWithBrokenMessageSchema(Path db) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db)) {
            try (var statement = connection.createStatement()) {
                statement.execute("""
                    create table sessions (
                        id text primary key,
                        name text,
                        session_type text,
                        working_dir text,
                        created_at text,
                        updated_at text,
                        total_tokens integer,
                        input_tokens integer,
                        output_tokens integer,
                        accumulated_total_tokens integer,
                        accumulated_input_tokens integer,
                        accumulated_output_tokens integer,
                        schedule_id text,
                        recipe_json text,
                        provider_name text,
                        model_config_json text,
                        goose_mode text,
                        thread_id text
                    )
                    """);
            }
            try (var statement = connection.createStatement()) {
                statement.execute("""
                    create table messages (
                        id integer primary key,
                        session_id text
                    )
                    """);
            }
        }
    }

    private static void insertSession(Connection connection,
                                      String id,
                                      String name,
                                      String sessionType,
                                      String scheduleId,
                                      String recipeJson,
                                      String providerName,
                                      String modelConfigJson,
                                      long totalTokens,
                                      long inputTokens,
                                      long outputTokens) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
            insert into sessions (
                id, name, session_type, working_dir, created_at, updated_at,
                total_tokens, input_tokens, output_tokens,
                accumulated_total_tokens, accumulated_input_tokens, accumulated_output_tokens,
                schedule_id, recipe_json, provider_name, model_config_json, goose_mode, thread_id
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)) {
            statement.setString(1, id);
            statement.setString(2, name);
            statement.setString(3, sessionType);
            statement.setString(4, "/workspace");
            statement.setString(5, "2026-05-20T10:00:00Z");
            statement.setString(6, "2026-05-20T11:00:00Z");
            statement.setLong(7, totalTokens);
            statement.setLong(8, inputTokens);
            statement.setLong(9, outputTokens);
            statement.setLong(10, totalTokens);
            statement.setLong(11, inputTokens);
            statement.setLong(12, outputTokens);
            statement.setString(13, scheduleId);
            statement.setString(14, recipeJson);
            statement.setString(15, providerName);
            statement.setString(16, modelConfigJson);
            statement.setString(17, "auto");
            statement.setString(18, "thread-" + id);
            statement.executeUpdate();
        }
    }

    private static void insertMessage(Connection connection, String id, String sessionId, String role, String content, int timestamp) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
            insert into messages (message_id, session_id, role, content_json, created_timestamp, timestamp, metadata_json)
            values (?, ?, ?, ?, ?, ?, ?)
            """)) {
            statement.setString(1, id);
            statement.setString(2, sessionId);
            statement.setString(3, role);
            statement.setString(4, content);
            statement.setInt(5, timestamp);
            statement.setInt(6, timestamp);
            statement.setString(7, "{\"userVisible\":true,\"agentVisible\":true}");
            statement.executeUpdate();
        }
    }
}
