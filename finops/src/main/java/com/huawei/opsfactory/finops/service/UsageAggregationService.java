package com.huawei.opsfactory.finops.service;

import com.huawei.opsfactory.finops.model.FinOpsModels.AgentUsage;
import com.huawei.opsfactory.finops.model.FinOpsModels.ComparisonSummary;
import com.huawei.opsfactory.finops.model.FinOpsModels.DistributionItem;
import com.huawei.opsfactory.finops.model.FinOpsModels.ModelUsage;
import com.huawei.opsfactory.finops.model.FinOpsModels.QueryFilter;
import com.huawei.opsfactory.finops.model.FinOpsModels.SessionUsageRecord;
import com.huawei.opsfactory.finops.model.FinOpsModels.TaskExecutionLoad;
import com.huawei.opsfactory.finops.model.FinOpsModels.TrendPoint;
import com.huawei.opsfactory.finops.model.FinOpsModels.UsageFilterRequest;
import com.huawei.opsfactory.finops.model.FinOpsModels.UsageSummary;
import com.huawei.opsfactory.finops.model.FinOpsModels.UserUsage;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Aggregates raw session usage records into FinOps summaries and distributions.
 *
 * @since 2026-05-28
 */
@Service
public class UsageAggregationService {

    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    /**
     * Builds a normalized query filter from request parameters.
     *
     * @param request raw filter request
     * @return normalized query filter
     */
    public QueryFilter buildFilter(UsageFilterRequest request) {
        Instant end = parseInstant(request.endTime(), Instant.now());
        Instant start = parseInstant(request.startTime(), end.minus(Duration.ofDays(30)));
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("startTime must be before endTime");
        }
        return new QueryFilter(
            start,
            end,
            blankToNull(request.agentId()),
            blankToNull(request.userId()),
            blankToNull(request.sessionType()),
            blankToNull(request.providerName()),
            blankToNull(request.modelName()),
            Boolean.TRUE.equals(request.compare())
        );
    }

    /**
     * Filters sessions inside the current query range.
     *
     * @param sessions source sessions
     * @param filter normalized query filter
     * @return filtered current sessions
     */
    public List<SessionUsageRecord> filterCurrent(List<SessionUsageRecord> sessions, QueryFilter filter) {
        return sessions.stream()
            .filter(session -> !session.updatedAt().isBefore(filter.startTime()) && session.updatedAt().isBefore(filter.endTime()))
            .filter(session -> filter.agentId() == null || filter.agentId().equals(session.agentId()))
            .filter(session -> filter.userId() == null || filter.userId().equals(session.userId()))
            .filter(session -> filter.sessionType() == null || filter.sessionType().equalsIgnoreCase(session.sessionType()))
            .filter(session -> filter.providerName() == null || filter.providerName().equalsIgnoreCase(session.providerName()))
            .filter(session -> filter.modelName() == null || filter.modelName().equalsIgnoreCase(session.modelName()))
            .toList();
    }

    /**
     * Filters sessions inside the previous comparison range.
     *
     * @param sessions source sessions
     * @param filter normalized query filter
     * @return filtered previous sessions
     */
    public List<SessionUsageRecord> filterPrevious(List<SessionUsageRecord> sessions, QueryFilter filter) {
        Duration duration = Duration.between(filter.startTime(), filter.endTime());
        Instant previousStart = filter.startTime().minus(duration);
        Instant previousEnd = filter.startTime();
        return sessions.stream()
            .filter(session -> !session.updatedAt().isBefore(previousStart) && session.updatedAt().isBefore(previousEnd))
            .filter(session -> filter.agentId() == null || filter.agentId().equals(session.agentId()))
            .filter(session -> filter.userId() == null || filter.userId().equals(session.userId()))
            .filter(session -> filter.sessionType() == null || filter.sessionType().equalsIgnoreCase(session.sessionType()))
            .filter(session -> filter.providerName() == null || filter.providerName().equalsIgnoreCase(session.providerName()))
            .filter(session -> filter.modelName() == null || filter.modelName().equalsIgnoreCase(session.modelName()))
            .toList();
    }

    /**
     * Builds current and previous period comparison metrics.
     *
     * @param current current period sessions
     * @param previous previous period sessions
     * @return comparison summary
     */
    public ComparisonSummary comparison(List<SessionUsageRecord> current, List<SessionUsageRecord> previous) {
        UsageSummary currentSummary = summarize(current);
        UsageSummary previousSummary = summarize(previous);
        long tokenDelta = currentSummary.totalTokens() - previousSummary.totalTokens();
        long sessionDelta = currentSummary.sessionCount() - previousSummary.sessionCount();
        return new ComparisonSummary(
            currentSummary,
            previousSummary,
            tokenDelta,
            growthRate(tokenDelta, previousSummary.totalTokens()),
            sessionDelta,
            growthRate(sessionDelta, previousSummary.sessionCount())
        );
    }

    /**
     * Summarizes session usage metrics.
     *
     * @param sessions source sessions
     * @return usage summary
     */
    public UsageSummary summarize(List<SessionUsageRecord> sessions) {
        long total = sessions.stream().mapToLong(SessionUsageRecord::totalTokens).sum();
        long input = sessions.stream().mapToLong(SessionUsageRecord::inputTokens).sum();
        long output = sessions.stream().mapToLong(SessionUsageRecord::outputTokens).sum();
        long scheduled = sessions.stream().filter(this::isScheduled).count();
        long activeModels = sessions.stream().map(this::providerModel).distinct().count();
        return new UsageSummary(
            sessions.size(),
            total,
            input,
            output,
            sessions.stream().map(SessionUsageRecord::userId).distinct().count(),
            sessions.stream().map(SessionUsageRecord::agentId).distinct().count(),
            activeModels,
            scheduled,
            sessions.size() - scheduled,
            sessions.isEmpty() ? 0 : (double) total / sessions.size()
        );
    }

    /**
     * Groups token usage by day.
     *
     * @param sessions source sessions
     * @return ordered trend points
     */
    public List<TrendPoint> tokenTrend(List<SessionUsageRecord> sessions) {
        Map<String, List<SessionUsageRecord>> byDay = sessions.stream()
            .collect(Collectors.groupingBy(session -> DAY_FORMATTER.format(session.updatedAt()), LinkedHashMap::new, Collectors.toList()));
        return byDay.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> new TrendPoint(
                entry.getKey(),
                entry.getValue().size(),
                entry.getValue().stream().mapToLong(SessionUsageRecord::totalTokens).sum(),
                entry.getValue().stream().mapToLong(SessionUsageRecord::inputTokens).sum(),
                entry.getValue().stream().mapToLong(SessionUsageRecord::outputTokens).sum()
            ))
            .toList();
    }

    /**
     * Aggregates sessions by agent.
     *
     * @param sessions source sessions
     * @return agent usage rows ordered by token count
     */
    public List<AgentUsage> agents(List<SessionUsageRecord> sessions) {
        return group(sessions, SessionUsageRecord::agentId).entrySet().stream()
            .map(entry -> {
                List<SessionUsageRecord> items = entry.getValue();
                long total = items.stream().mapToLong(SessionUsageRecord::totalTokens).sum();
                return new AgentUsage(
                    entry.getKey(),
                    items.stream().map(SessionUsageRecord::userId).distinct().count(),
                    items.size(),
                    total,
                    items.stream().mapToLong(SessionUsageRecord::inputTokens).sum(),
                    items.stream().mapToLong(SessionUsageRecord::outputTokens).sum(),
                    items.isEmpty() ? 0 : (double) total / items.size(),
                    items.stream().filter(this::isScheduled).count(),
                    highTokenSessionCount(items)
                );
            })
            .sorted(Comparator.comparingLong(AgentUsage::totalTokens).reversed())
            .toList();
    }

    /**
     * Aggregates sessions by user.
     *
     * @param sessions source sessions
     * @return user usage rows ordered by token count
     */
    public List<UserUsage> users(List<SessionUsageRecord> sessions) {
        return group(sessions, SessionUsageRecord::userId).entrySet().stream()
            .map(entry -> {
                List<SessionUsageRecord> items = entry.getValue();
                long total = items.stream().mapToLong(SessionUsageRecord::totalTokens).sum();
                return new UserUsage(
                    entry.getKey(),
                    items.stream().map(SessionUsageRecord::agentId).distinct().count(),
                    items.size(),
                    total,
                    items.stream().mapToLong(SessionUsageRecord::inputTokens).sum(),
                    items.stream().mapToLong(SessionUsageRecord::outputTokens).sum(),
                    items.isEmpty() ? 0 : (double) total / items.size(),
                    items.stream().map(SessionUsageRecord::updatedAt).max(Instant::compareTo).orElse(null),
                    topByTokens(items, SessionUsageRecord::agentId)
                );
            })
            .sorted(Comparator.comparingLong(UserUsage::totalTokens).reversed())
            .toList();
    }

    /**
     * Aggregates sessions by provider and model.
     *
     * @param sessions source sessions
     * @return model usage rows ordered by token count
     */
    public List<ModelUsage> models(List<SessionUsageRecord> sessions) {
        return sessions.stream()
            .collect(Collectors.groupingBy(this::providerModel))
            .entrySet().stream()
            .map(entry -> {
                List<SessionUsageRecord> items = entry.getValue();
                ProviderModel key = entry.getKey();
                long total = items.stream().mapToLong(SessionUsageRecord::totalTokens).sum();
                return new ModelUsage(
                    key.providerName(),
                    key.modelName(),
                    items.size(),
                    items.stream().map(SessionUsageRecord::userId).distinct().count(),
                    items.stream().map(SessionUsageRecord::agentId).distinct().count(),
                    total,
                    items.stream().mapToLong(SessionUsageRecord::inputTokens).sum(),
                    items.stream().mapToLong(SessionUsageRecord::outputTokens).sum(),
                    items.isEmpty() ? 0 : (double) total / items.size()
                );
            })
            .sorted(Comparator.comparingLong(ModelUsage::totalTokens).reversed())
            .toList();
    }

    private ProviderModel providerModel(SessionUsageRecord session) {
        return new ProviderModel(normalizeDimension(session.providerName()), normalizeDimension(session.modelName()));
    }

    /**
     * Builds a token distribution by the supplied classifier.
     *
     * @param sessions source sessions
     * @param classifier grouping classifier
     * @return distribution rows ordered by token count
     */
    public List<DistributionItem> distribution(List<SessionUsageRecord> sessions, Function<SessionUsageRecord, String> classifier) {
        long totalTokens = sessions.stream().mapToLong(SessionUsageRecord::totalTokens).sum();
        return group(sessions, classifier).entrySet().stream()
            .map(entry -> {
                long tokens = entry.getValue().stream().mapToLong(SessionUsageRecord::totalTokens).sum();
                return new DistributionItem(entry.getKey(), entry.getKey(), entry.getValue().size(), tokens, totalTokens == 0 ? 0 : (double) tokens / totalTokens);
            })
            .sorted(Comparator.comparingLong(DistributionItem::totalTokens).reversed())
            .toList();
    }

    /**
     * Calculates task execution load using each session as one task.
     *
     * @param sessions source sessions
     * @return task execution load summary
     */
    public TaskExecutionLoad taskExecutionLoad(List<SessionUsageRecord> sessions) {
        if (sessions.isEmpty()) {
            return new TaskExecutionLoad(0, 0, 0);
        }
        long totalTokens = sessions.stream().mapToLong(SessionUsageRecord::totalTokens).sum();
        long totalMessages = sessions.stream().mapToLong(SessionUsageRecord::messageCount).sum();
        long totalToolResponses = sessions.stream().mapToLong(SessionUsageRecord::toolResponseCount).sum();
        return new TaskExecutionLoad(
            (double) totalTokens / sessions.size(),
            (double) totalMessages / sessions.size(),
            (double) totalToolResponses / sessions.size()
        );
    }

    /**
     * Returns the highest-token sessions.
     *
     * @param sessions source sessions
     * @param limit maximum number of rows
     * @return top sessions ordered by token count
     */
    public List<SessionUsageRecord> topSessions(List<SessionUsageRecord> sessions, int limit) {
        return sessions.stream()
            .sorted(Comparator.comparingLong(SessionUsageRecord::totalTokens).reversed())
            .limit(limit)
            .toList();
    }

    private Map<String, List<SessionUsageRecord>> group(List<SessionUsageRecord> sessions, Function<SessionUsageRecord, String> classifier) {
        return sessions.stream().collect(Collectors.groupingBy(session -> normalizeDimension(classifier.apply(session)), LinkedHashMap::new, Collectors.toList()));
    }

    private String normalizeDimension(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private long highTokenSessionCount(List<SessionUsageRecord> sessions) {
        if (sessions.isEmpty()) {
            return 0;
        }
        long p90 = percentile90(sessions.stream().map(SessionUsageRecord::totalTokens).toList());
        return sessions.stream().filter(session -> session.totalTokens() >= p90 && session.totalTokens() > 0).count();
    }

    private long percentile90(List<Long> values) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Long> sorted = values.stream().sorted().toList();
        return sorted.get(Math.max(0, (int) Math.floor((sorted.size() - 1) * 0.9)));
    }

    private String topByTokens(List<SessionUsageRecord> sessions, Function<SessionUsageRecord, String> classifier) {
        return group(sessions, classifier).entrySet().stream()
            .max(Comparator.comparingLong(entry -> entry.getValue().stream().mapToLong(SessionUsageRecord::totalTokens).sum()))
            .map(Map.Entry::getKey)
            .orElse("");
    }

    private boolean isScheduled(SessionUsageRecord session) {
        return "scheduled".equalsIgnoreCase(session.sessionType()) || (session.scheduleId() != null && !session.scheduleId().isBlank());
    }

    private Double growthRate(long delta, long previous) {
        return previous == 0 ? null : (double) delta / previous;
    }

    private Instant parseInstant(String value, Instant fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid timestamp: " + value, ex);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record ProviderModel(String providerName, String modelName) {
    }
}
