/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.model.finops;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Data contracts exposed by the gateway usage snapshot endpoint.
 *
 * @since 2026-05-28
 */
public final class UsageSnapshotModels {

    private UsageSnapshotModels() {
    }

    /**
     * Snapshot payload containing normalized session and message usage facts.
     *
     * @param sessions normalized session usage records
     * @param messages normalized message records
     * @param sourceDbCount number of source session databases discovered
     * @param skippedDbCount number of source session databases skipped during reading
     * @param dataSource source data root used for the scan
     * @param lastError latest source read error, if any
     */
    public record SnapshotPayload(
        List<SessionUsageRecord> sessions,
        List<SessionMessageRecord> messages,
        int sourceDbCount,
        int skippedDbCount,
        String dataSource,
        String lastError
    ) {
    }

    /**
     * Normalized session-level usage record.
     *
     * @param id session identifier
     * @param userId user identifier
     * @param agentId agent identifier
     * @param name session name
     * @param sessionType session type
     * @param workingDir working directory
     * @param createdAt creation time
     * @param updatedAt update time
     * @param totalTokens total token count
     * @param inputTokens input token count
     * @param outputTokens output token count
     * @param accumulatedTotalTokens accumulated total token count
     * @param accumulatedInputTokens accumulated input token count
     * @param accumulatedOutputTokens accumulated output token count
     * @param scheduleId schedule identifier
     * @param providerName provider name
     * @param modelName model name
     * @param gooseMode goose mode
     * @param threadId thread identifier
     * @param messageCount message count
     * @param userMessageCount user message count
     * @param assistantMessageCount assistant message count
     * @param toolResponseCount tool response count
     * @param label display label
     * @param modelConfig raw model config
     * @param recipe raw recipe config
     */
    public record SessionUsageRecord(
        String id,
        String userId,
        String agentId,
        String name,
        String sessionType,
        String workingDir,
        Instant createdAt,
        Instant updatedAt,
        long totalTokens,
        long inputTokens,
        long outputTokens,
        long accumulatedTotalTokens,
        long accumulatedInputTokens,
        long accumulatedOutputTokens,
        String scheduleId,
        String providerName,
        String modelName,
        String gooseMode,
        String threadId,
        int messageCount,
        int userMessageCount,
        int assistantMessageCount,
        int toolResponseCount,
        String label,
        Map<String, Object> modelConfig,
        Map<String, Object> recipe
    ) {
    }

    /**
     * Normalized message-level record for session detail inspection.
     *
     * @param sessionId session identifier
     * @param userId user identifier
     * @param agentId agent identifier
     * @param messageId message identifier
     * @param rowId database row identifier
     * @param role message role
     * @param createdAt creation time
     * @param insertedAt insert time
     * @param tokens token count
     * @param contentLength content length
     * @param contentPreview content preview
     * @param contentText normalized content text
     * @param contentTruncated whether content text was truncated
     * @param toolRequest whether message contains a tool request
     * @param toolResponse whether message contains a tool response
     * @param toolName tool name
     * @param error whether message indicates an error
     * @param userVisible whether message is user-visible
     * @param agentVisible whether message is agent-visible
     */
    public record SessionMessageRecord(
        String sessionId,
        String userId,
        String agentId,
        String messageId,
        long rowId,
        String role,
        Instant createdAt,
        Instant insertedAt,
        Long tokens,
        int contentLength,
        String contentPreview,
        String contentText,
        boolean contentTruncated,
        boolean toolRequest,
        boolean toolResponse,
        String toolName,
        boolean error,
        boolean userVisible,
        boolean agentVisible
    ) {
    }
}
