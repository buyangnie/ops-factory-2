package com.huawei.opsfactory.finops.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * API and internal data models used by the FinOps service.
 *
 * @since 2026-05-28
 */
public final class FinOpsModels {

    private FinOpsModels() {
    }

    /**
     * Period scope for comparison calculations.
     */
    public enum SessionScope {
        CURRENT,
        PREVIOUS
    }

    /**
     * Normalized query filter used by aggregation.
     *
     * @param startTime inclusive range start
     * @param endTime exclusive range end
     * @param agentId optional agent filter
     * @param userId optional user filter
     * @param sessionType optional session type filter
     * @param providerName optional provider filter
     * @param modelName optional model filter
     * @param compare whether comparison is requested
     */
    public record QueryFilter(
        Instant startTime,
        Instant endTime,
        String agentId,
        String userId,
        String sessionType,
        String providerName,
        String modelName,
        boolean compare
    ) {
    }

    /**
     * Raw usage filter request values from API queries.
     *
     * @param startTime requested range start
     * @param endTime requested range end
     * @param agentId requested agent filter
     * @param userId requested user filter
     * @param sessionType requested session type filter
     * @param providerName requested provider filter
     * @param modelName requested model filter
     * @param compare requested comparison flag
     */
    public record UsageFilterRequest(
        String startTime,
        String endTime,
        String agentId,
        String userId,
        String sessionType,
        String providerName,
        String modelName,
        Boolean compare
    ) {
    }

    /**
     * Internal session usage record from gateway snapshots.
     *
     * @param id session identifier
     * @param userId user identifier
     * @param agentId agent identifier
     * @param name session name
     * @param sessionType session type
     * @param workingDir working directory
     * @param createdAt session creation time
     * @param updatedAt session update time
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
     * Session usage record returned by FinOps APIs.
     *
     * @param id session identifier
     * @param userId user identifier
     * @param agentId agent identifier
     * @param name session name
     * @param sessionType session type
     * @param createdAt creation time
     * @param updatedAt update time
     * @param totalTokens total token count
     * @param inputTokens input token count
     * @param outputTokens output token count
     * @param scheduleId schedule identifier
     * @param providerName provider name
     * @param modelName model name
     * @param messageCount message count
     * @param userMessageCount user message count
     * @param assistantMessageCount assistant message count
     * @param toolResponseCount tool response count
     * @param label display label
     */
    public record SessionUsage(
        String id,
        String userId,
        String agentId,
        String name,
        String sessionType,
        Instant createdAt,
        Instant updatedAt,
        long totalTokens,
        long inputTokens,
        long outputTokens,
        String scheduleId,
        String providerName,
        String modelName,
        int messageCount,
        int userMessageCount,
        int assistantMessageCount,
        int toolResponseCount,
        String label
    ) {
    }

    /**
     * Internal session message record from gateway snapshots.
     *
     * @param sessionId session identifier
     * @param userId user identifier
     * @param agentId agent identifier
     * @param messageId message identifier
     * @param rowId database row identifier
     * @param role message role
     * @param createdAt message creation time
     * @param insertedAt message insert time
     * @param tokens message token count
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

    /**
     * Gateway snapshot payload consumed by FinOps.
     *
     * @param sessions session usage records
     * @param messages session message records
     * @param sourceDbCount number of discovered source databases
     * @param skippedDbCount number of skipped source databases
     * @param dataSource source data root
     * @param lastError latest source read error
     */
    public record UsageSnapshotPayload(
        List<SessionUsageRecord> sessions,
        List<SessionMessageRecord> messages,
        int sourceDbCount,
        int skippedDbCount,
        String dataSource,
        String lastError
    ) {
    }

    /**
     * Session message detail returned by FinOps APIs.
     *
     * @param messageId message identifier
     * @param rowId database row identifier
     * @param role message role
     * @param createdAt message creation time
     * @param insertedAt message insert time
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
    public record SessionMessageDetail(
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

    /**
     * Aggregate message statistics for one session.
     *
     * @param messageCount total message count
     * @param userMessageCount user message count
     * @param assistantMessageCount assistant message count
     * @param toolRequestCount tool request count
     * @param toolResponseCount tool response count
     * @param messagesWithTokenCount messages that have token counts
     * @param largestContentLength largest content length
     * @param largestContentRole role of the largest content message
     * @param largestContentPreview preview of the largest content message
     */
    public record SessionMessageStats(
        int messageCount,
        int userMessageCount,
        int assistantMessageCount,
        int toolRequestCount,
        int toolResponseCount,
        int messagesWithTokenCount,
        int largestContentLength,
        String largestContentRole,
        String largestContentPreview
    ) {
    }

    /**
     * Capability flags for session message detail data.
     *
     * @param messageTokenAvailable whether message-level token values exist
     * @param contentPreviewAvailable whether content previews exist
     * @param toolSignalAvailable whether tool signals exist
     */
    public record SessionMessageCapabilities(
        boolean messageTokenAvailable,
        boolean contentPreviewAvailable,
        boolean toolSignalAvailable
    ) {
    }

    /**
     * Response containing one session and its message details.
     *
     * @param snapshotStatus snapshot status
     * @param session session usage record
     * @param stats message statistics
     * @param capabilities message capability flags
     * @param messages message detail rows
     */
    public record SessionMessagesResponse(
        SnapshotStatus snapshotStatus,
        SessionUsage session,
        SessionMessageStats stats,
        SessionMessageCapabilities capabilities,
        List<SessionMessageDetail> messages
    ) {
    }

    /**
     * Current snapshot status returned with every FinOps response.
     *
     * @param status snapshot status value
     * @param lastRefreshedAt last refresh time
     * @param sourceDbCount discovered source database count
     * @param skippedDbCount skipped source database count
     * @param sessionCount session count in the snapshot
     * @param lastRefreshError latest refresh error
     */
    public record SnapshotStatus(
        String status,
        Instant lastRefreshedAt,
        int sourceDbCount,
        int skippedDbCount,
        int sessionCount,
        String lastRefreshError
    ) {
    }

    /**
     * Summary usage metrics for a session set.
     *
     * @param sessionCount session count
     * @param totalTokens total token count
     * @param inputTokens input token count
     * @param outputTokens output token count
     * @param activeUsers active user count
     * @param activeAgents active agent count
     * @param activeModels active model count
     * @param scheduledSessionCount scheduled session count
     * @param manualSessionCount manual session count
     * @param avgTokensPerSession average tokens per session
     */
    public record UsageSummary(
        long sessionCount,
        long totalTokens,
        long inputTokens,
        long outputTokens,
        long activeUsers,
        long activeAgents,
        long activeModels,
        long scheduledSessionCount,
        long manualSessionCount,
        double avgTokensPerSession
    ) {
    }

    /**
     * Comparison between current and previous periods.
     *
     * @param current current period summary
     * @param previous previous period summary
     * @param tokenDelta token delta
     * @param tokenGrowthRate token growth rate
     * @param sessionDelta session delta
     * @param sessionGrowthRate session growth rate
     */
    public record ComparisonSummary(
        UsageSummary current,
        UsageSummary previous,
        Long tokenDelta,
        Double tokenGrowthRate,
        Long sessionDelta,
        Double sessionGrowthRate
    ) {
    }

    /**
     * Token trend point.
     *
     * @param bucket time bucket
     * @param sessionCount session count
     * @param totalTokens total token count
     * @param inputTokens input token count
     * @param outputTokens output token count
     */
    public record TrendPoint(
        String bucket,
        long sessionCount,
        long totalTokens,
        long inputTokens,
        long outputTokens
    ) {
    }

    /**
     * Generic distribution row.
     *
     * @param id distribution identifier
     * @param label display label
     * @param sessionCount session count
     * @param totalTokens total token count
     * @param percentage token percentage
     */
    public record DistributionItem(
        String id,
        String label,
        long sessionCount,
        long totalTokens,
        double percentage
    ) {
    }

    /**
     * Task execution load metrics.
     *
     * @param avgTokensPerTask average tokens per task
     * @param avgMessagesPerTask average messages per task
     * @param avgToolResponsesPerTask average tool responses per task
     */
    public record TaskExecutionLoad(
        double avgTokensPerTask,
        double avgMessagesPerTask,
        double avgToolResponsesPerTask
    ) {
    }

    /**
     * Agent usage row.
     *
     * @param agentId agent identifier
     * @param activeUsers active user count
     * @param sessionCount session count
     * @param totalTokens total token count
     * @param inputTokens input token count
     * @param outputTokens output token count
     * @param avgTokensPerSession average tokens per session
     * @param scheduledSessionCount scheduled session count
     * @param highTokenSessionCount high token session count
     */
    public record AgentUsage(
        String agentId,
        long activeUsers,
        long sessionCount,
        long totalTokens,
        long inputTokens,
        long outputTokens,
        double avgTokensPerSession,
        long scheduledSessionCount,
        long highTokenSessionCount
    ) {
    }

    /**
     * User usage row.
     *
     * @param userId user identifier
     * @param activeAgents active agent count
     * @param sessionCount session count
     * @param totalTokens total token count
     * @param inputTokens input token count
     * @param outputTokens output token count
     * @param avgTokensPerSession average tokens per session
     * @param lastActiveAt last active time
     * @param topAgent highest-token agent
     */
    public record UserUsage(
        String userId,
        long activeAgents,
        long sessionCount,
        long totalTokens,
        long inputTokens,
        long outputTokens,
        double avgTokensPerSession,
        Instant lastActiveAt,
        String topAgent
    ) {
    }

    /**
     * Model usage row.
     *
     * @param providerName provider name
     * @param modelName model name
     * @param sessionCount session count
     * @param activeUsers active user count
     * @param activeAgents active agent count
     * @param totalTokens total token count
     * @param inputTokens input token count
     * @param outputTokens output token count
     * @param avgTokensPerSession average tokens per session
     */
    public record ModelUsage(
        String providerName,
        String modelName,
        long sessionCount,
        long activeUsers,
        long activeAgents,
        long totalTokens,
        long inputTokens,
        long outputTokens,
        double avgTokensPerSession
    ) {
    }

    /**
     * Overview dashboard response.
     *
     * @param snapshotStatus snapshot status
     * @param summary comparison summary
     * @param tokenTrend token trend points
     * @param topAgents top agent usage rows
     * @param topUsers top user usage rows
     * @param topSessions top session usage rows
     * @param models model usage rows
     * @param taskExecutionLoad task execution load metrics
     * @param sessionTypeDistribution session type distribution
     * @param providerDistribution provider distribution
     */
    public record OverviewResponse(
        SnapshotStatus snapshotStatus,
        ComparisonSummary summary,
        List<TrendPoint> tokenTrend,
        List<AgentUsage> topAgents,
        List<UserUsage> topUsers,
        List<SessionUsage> topSessions,
        List<ModelUsage> models,
        TaskExecutionLoad taskExecutionLoad,
        List<DistributionItem> sessionTypeDistribution,
        List<DistributionItem> providerDistribution
    ) {
    }

    /**
     * Simple list response.
     *
     * @param snapshotStatus snapshot status
     * @param items response items
     * @param <T> item type
     */
    public record ListResponse<T>(
        SnapshotStatus snapshotStatus,
        List<T> items
    ) {
    }

    /**
     * Paged list response.
     *
     * @param snapshotStatus snapshot status
     * @param items response items
     * @param page current page
     * @param size page size
     * @param totalItems total item count
     * @param totalPages total page count
     * @param <T> item type
     */
    public record PageResponse<T>(
        SnapshotStatus snapshotStatus,
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages
    ) {
    }

}
