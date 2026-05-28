/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service.finops;

import com.huawei.opsfactory.gateway.model.finops.UsageSnapshotModels.SessionMessageRecord;
import com.huawei.opsfactory.gateway.model.finops.UsageSnapshotModels.SessionUsageRecord;
import com.huawei.opsfactory.gateway.model.finops.UsageSnapshotModels.SnapshotPayload;
import com.huawei.opsfactory.gateway.service.AgentConfigService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Builds usage snapshots from gateway-managed goosed session stores.
 *
 * @since 2026-05-28
 */
@Service
public class UsageSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(UsageSnapshotService.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final int MAX_DB_OPEN_SECONDS = 5;

    private final AgentConfigService agentConfigService;
    private final ObjectMapper objectMapper;
    private final UsageSnapshotContentParser contentParser;
    private final UsageSnapshotTimeParser timeParser = new UsageSnapshotTimeParser();

    /**
     * Creates the gateway usage snapshot service.
     *
     * @param agentConfigService gateway agent configuration service
     * @param objectMapper JSON object mapper
     */
    public UsageSnapshotService(AgentConfigService agentConfigService, ObjectMapper objectMapper) {
        this.agentConfigService = agentConfigService;
        this.objectMapper = objectMapper;
        this.contentParser = new UsageSnapshotContentParser(objectMapper);
    }

    /**
     * Returns a read-only usage snapshot for every goosed session database under the gateway users directory.
     *
     * @return normalized usage snapshot payload
     */
    public SnapshotPayload snapshot() {
        Path dataRoot = agentConfigService.getUsersDir().toAbsolutePath().normalize();
        if (!Files.isDirectory(dataRoot)) {
            log.warn("Usage snapshot data root does not exist or is not a directory: {}", dataRoot);
            return new SnapshotPayload(List.of(), List.of(), 0, 0, dataRoot.toString(), "Data root not found: " + dataRoot);
        }

        List<Path> dbs;
        try (var stream = Files.find(dataRoot, 6, (path, attrs) -> attrs.isRegularFile() && isSessionDb(path))) {
            dbs = stream.sorted().toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to scan usage snapshot data root " + dataRoot, ex);
        }

        List<SessionUsageRecord> sessions = new ArrayList<>();
        List<SessionMessageRecord> messages = new ArrayList<>();
        int skipped = 0;
        String lastError = null;
        for (Path db : dbs) {
            try {
                DbReadResult result = readDb(dataRoot, db);
                sessions.addAll(result.sessions());
                messages.addAll(result.messages());
            } catch (SQLException ex) {
                skipped++;
                lastError = db + ": " + ex.getMessage();
                log.warn("Skipping unreadable FinOps session DB {}", db, ex);
            }
        }
        return new SnapshotPayload(sessions, messages, dbs.size(), skipped, dataRoot.toString(), lastError);
    }

    private boolean isSessionDb(Path path) {
        String name = path.getFileName().toString();
        if (!"sessions.db".equals(name)) {
            return false;
        }
        String normalized = path.toString().replace('\\', '/');
        return normalized.contains("/agents/") && (
            normalized.endsWith("/data/sessions/sessions.db") ||
            normalized.endsWith("/data/sessions.db")
        );
    }

    private DbReadResult readDb(Path dataRoot, Path db) throws SQLException {
        Optional<UserAgent> userAgent = resolveUserAgent(dataRoot, db);
        if (userAgent.isEmpty()) {
            return new DbReadResult(List.of(), List.of());
        }

        String url = "jdbc:sqlite:file:" + db.toAbsolutePath() + "?mode=ro";
        try (Connection connection = DriverManager.getConnection(url)) {
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(MAX_DB_OPEN_SECONDS);
                statement.execute("PRAGMA query_only = true");
            }
            Map<String, MessageStats> messageStats = readMessageStats(connection);
            return new DbReadResult(
                readSessions(connection, userAgent.get(), messageStats),
                readMessages(connection, userAgent.get())
            );
        }
    }

    private Optional<UserAgent> resolveUserAgent(Path dataRoot, Path db) {
        Path relative = dataRoot.relativize(db);
        if (relative.getNameCount() < 4 || !"agents".equals(relative.getName(1).toString())) {
            return Optional.empty();
        }
        return Optional.of(new UserAgent(relative.getName(0).toString(), relative.getName(2).toString()));
    }

    private Map<String, MessageStats> readMessageStats(Connection connection) throws SQLException {
        if (!tableExists(connection, "messages")) {
            return Map.of();
        }

        Map<String, MutableMessageStats> stats = new HashMap<>();
        String sql = """
            select session_id, role, content_json, created_timestamp, timestamp
            from messages
            order by coalesce(created_timestamp, timestamp, 0) asc
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                String sessionId = rs.getString("session_id");
                if (sessionId == null || sessionId.isBlank()) {
                    continue;
                }
                MutableMessageStats item = stats.computeIfAbsent(sessionId, ignored -> new MutableMessageStats());
                String role = nullToEmpty(rs.getString("role"));
                String content = rs.getString("content_json");
                item.messageCount++;
                if ("user".equalsIgnoreCase(role)) {
                    item.userMessageCount++;
                    if (item.firstUserText == null) {
                        item.firstUserText = contentParser.extractFirstText(content);
                    }
                } else if ("assistant".equalsIgnoreCase(role)) {
                    item.assistantMessageCount++;
                }
                if (contentParser.containsToolResponse(content)) {
                    item.toolResponseCount++;
                }
            }
        }
        Map<String, MessageStats> result = new HashMap<>();
        stats.forEach((key, value) -> result.put(key, value.toRecord()));
        return result;
    }

    private List<SessionUsageRecord> readSessions(Connection connection, UserAgent userAgent, Map<String, MessageStats> messageStats) throws SQLException {
        if (!tableExists(connection, "sessions")) {
            return List.of();
        }
        List<SessionUsageRecord> records = new ArrayList<>();
        String sql = """
            select id, name, session_type, working_dir, created_at, updated_at,
                   total_tokens, input_tokens, output_tokens,
                   accumulated_total_tokens, accumulated_input_tokens, accumulated_output_tokens,
                   schedule_id, recipe_json, provider_name, model_config_json, goose_mode, thread_id
            from sessions
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                String sessionId = rs.getString("id");
                if (sessionId == null || sessionId.isBlank()) {
                    continue;
                }
                String recipeJson = rs.getString("recipe_json");
                String modelConfigJson = rs.getString("model_config_json");
                Map<String, Object> recipe = parseMap(recipeJson);
                Map<String, Object> modelConfig = parseMap(modelConfigJson);
                MessageStats stats = messageStats.getOrDefault(sessionId, MessageStats.EMPTY);
                String name = rs.getString("name");
                records.add(new SessionUsageRecord(
                    sessionId,
                    userAgent.userId(),
                    userAgent.agentId(),
                    name,
                    normalizeSessionType(rs.getString("session_type"), rs.getString("schedule_id")),
                    rs.getString("working_dir"),
                    timeParser.parseInstant(rs.getObject("created_at")),
                    timeParser.parseInstant(rs.getObject("updated_at")),
                    longValue(rs.getObject("total_tokens")),
                    longValue(rs.getObject("input_tokens")),
                    longValue(rs.getObject("output_tokens")),
                    longValue(rs.getObject("accumulated_total_tokens")),
                    longValue(rs.getObject("accumulated_input_tokens")),
                    longValue(rs.getObject("accumulated_output_tokens")),
                    rs.getString("schedule_id"),
                    blankToUnknown(rs.getString("provider_name")),
                    extractModelName(modelConfig),
                    rs.getString("goose_mode"),
                    rs.getString("thread_id"),
                    stats.messageCount(),
                    stats.userMessageCount(),
                    stats.assistantMessageCount(),
                    stats.toolResponseCount(),
                    buildLabel(name, recipe, stats.firstUserText()),
                    modelConfig,
                    recipe
                ));
            }
        }
        return records;
    }

    private List<SessionMessageRecord> readMessages(Connection connection, UserAgent userAgent) throws SQLException {
        if (!tableExists(connection, "messages")) {
            return List.of();
        }

        List<SessionMessageRecord> records = new ArrayList<>();
        String sql = """
            select id, message_id, session_id, role, content_json, created_timestamp, timestamp, tokens, metadata_json
            from messages
            order by session_id asc, coalesce(created_timestamp, timestamp, 0) asc, id asc
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                String sessionId = rs.getString("session_id");
                if (sessionId == null || sessionId.isBlank()) {
                    continue;
                }
                String contentJson = rs.getString("content_json");
                UsageSnapshotContentParser.MessageContentSummary content = contentParser.summarizeContent(contentJson);
                UsageSnapshotContentParser.MessageMetadata metadata = contentParser.parseMetadata(rs.getString("metadata_json"));
                records.add(new SessionMessageRecord(
                    sessionId,
                    userAgent.userId(),
                    userAgent.agentId(),
                    blankToNull(rs.getString("message_id")),
                    longValue(rs.getObject("id")),
                    nullToEmpty(rs.getString("role")),
                    timeParser.parseInstant(rs.getObject("created_timestamp")),
                    timeParser.parseInstant(rs.getObject("timestamp")),
                    nullableLong(rs.getObject("tokens")),
                    content.contentLength(),
                    content.preview(),
                    content.text(),
                    content.truncated(),
                    content.toolRequest(),
                    content.toolResponse(),
                    content.toolName(),
                    content.error(),
                    metadata.userVisible(),
                    metadata.agentVisible()
                ));
            }
        }
        return records;
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select name from sqlite_master where type='table' and name=?")) {
            statement.setString(1, tableName);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            log.debug("Ignoring invalid FinOps JSON map", ex);
            return Map.of();
        }
    }

    private String extractModelName(Map<String, Object> modelConfig) {
        for (String key : List.of("model_name", "modelName", "model")) {
            Object value = modelConfig.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return "unknown";
    }

    private String buildLabel(String name, Map<String, Object> recipe, String firstUserText) {
        if (name != null && !name.isBlank() && !"New Chat".equalsIgnoreCase(name.trim())) {
            return truncate(name.trim(), 120);
        }
        String recipeTitle = stringValue(recipe.get("title"));
        if (!recipeTitle.isBlank()) {
            String description = stringValue(recipe.get("description"));
            return truncate(description.isBlank() ? recipeTitle : recipeTitle + " - " + description, 120);
        }
        return truncate(firstUserText == null || firstUserText.isBlank() ? "Session" : firstUserText.trim(), 120);
    }

    private String normalizeSessionType(String type, String scheduleId) {
        if (scheduleId != null && !scheduleId.isBlank()) {
            return "scheduled";
        }
        if (type == null || type.isBlank()) {
            return "manual";
        }
        return type.toLowerCase(Locale.ROOT);
    }

    private long longValue(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String blankToUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private Long nullableLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String truncate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private record DbReadResult(List<SessionUsageRecord> sessions, List<SessionMessageRecord> messages) {
    }

    private record UserAgent(String userId, String agentId) {
    }

    private record MessageStats(
        int messageCount,
        int userMessageCount,
        int assistantMessageCount,
        int toolResponseCount,
        String firstUserText
    ) {
        static final MessageStats EMPTY = new MessageStats(0, 0, 0, 0, null);
    }

    private static final class MutableMessageStats {
        private int messageCount;
        private int userMessageCount;
        private int assistantMessageCount;
        private int toolResponseCount;
        private String firstUserText;

        private MessageStats toRecord() {
            return new MessageStats(messageCount, userMessageCount, assistantMessageCount, toolResponseCount, firstUserText);
        }
    }
}
