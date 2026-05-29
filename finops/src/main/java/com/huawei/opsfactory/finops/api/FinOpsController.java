package com.huawei.opsfactory.finops.api;

import com.huawei.opsfactory.finops.model.FinOpsModels.AgentUsage;
import com.huawei.opsfactory.finops.model.FinOpsModels.ModelUsage;
import com.huawei.opsfactory.finops.model.FinOpsModels.OverviewResponse;
import com.huawei.opsfactory.finops.model.FinOpsModels.PageResponse;
import com.huawei.opsfactory.finops.model.FinOpsModels.QueryFilter;
import com.huawei.opsfactory.finops.model.FinOpsModels.SessionMessageCapabilities;
import com.huawei.opsfactory.finops.model.FinOpsModels.SessionMessageDetail;
import com.huawei.opsfactory.finops.model.FinOpsModels.SessionMessageRecord;
import com.huawei.opsfactory.finops.model.FinOpsModels.SessionMessageStats;
import com.huawei.opsfactory.finops.model.FinOpsModels.SessionMessagesResponse;
import com.huawei.opsfactory.finops.model.FinOpsModels.SessionUsage;
import com.huawei.opsfactory.finops.model.FinOpsModels.SessionUsageRecord;
import com.huawei.opsfactory.finops.model.FinOpsModels.SnapshotStatus;
import com.huawei.opsfactory.finops.model.FinOpsModels.UsageFilterRequest;
import com.huawei.opsfactory.finops.model.FinOpsModels.UserUsage;
import com.huawei.opsfactory.finops.service.UsageAggregationService;
import com.huawei.opsfactory.finops.service.UsageIngestionService;
import com.huawei.opsfactory.finops.store.FinOpsSnapshotStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Provides FinOps usage query and refresh endpoints.
 *
 * @since 2026-05-28
 */
@RestController
@RequestMapping("/finops")
public class FinOpsController {

    private final FinOpsSnapshotStore snapshotStore;
    private final UsageIngestionService ingestionService;
    private final UsageAggregationService aggregationService;

    /**
     * Creates the FinOps API controller.
     *
     * @param snapshotStore current snapshot store
     * @param ingestionService snapshot refresh service
     * @param aggregationService usage aggregation service
     */
    public FinOpsController(FinOpsSnapshotStore snapshotStore,
                            UsageIngestionService ingestionService,
                            UsageAggregationService aggregationService) {
        this.snapshotStore = snapshotStore;
        this.ingestionService = ingestionService;
        this.aggregationService = aggregationService;
    }

    /**
     * Returns the overview dashboard data for the requested filter.
     *
     * @param request overview query parameters
     * @return overview response
     */
    @GetMapping("/overview")
    public OverviewResponse overview(@ModelAttribute UsageQueryRequest request) {
        FinOpsSnapshotStore.Snapshot snapshot = snapshotStore.current();
        QueryFilter filter = filter(request);
        List<SessionUsageRecord> current = aggregationService.filterCurrent(snapshot.sessions(), filter);
        List<SessionUsageRecord> previous = aggregationService.filterPrevious(snapshot.sessions(), filter);
        return new OverviewResponse(
            snapshot.status(),
            aggregationService.comparison(current, previous),
            aggregationService.tokenTrend(current),
            aggregationService.agents(current).stream().limit(10).toList(),
            aggregationService.users(current).stream().limit(10).toList(),
            aggregationService.topSessions(current, 10).stream().map(FinOpsController::toSessionUsage).toList(),
            aggregationService.models(current).stream().limit(10).toList(),
            aggregationService.taskExecutionLoad(current),
            aggregationService.distribution(current, SessionUsageRecord::sessionType),
            aggregationService.distribution(current, SessionUsageRecord::providerName)
        );
    }

    /**
     * Returns paged agent usage rows.
     *
     * @param request list query parameters
     * @return paged agent usage response
     */
    @GetMapping("/agents")
    public PageResponse<AgentUsage> agents(@ModelAttribute UsageQueryRequest request) {
        var snapshot = snapshotStore.current();
        var filter = filter(request.withScope(null, null, null, null, null, false));
        return page(snapshot.status(), aggregationService.agents(aggregationService.filterCurrent(snapshot.sessions(), filter)), request.getPage(), request.getSize());
    }

    /**
     * Returns paged sessions for one agent.
     *
     * @param agentId agent identifier
     * @param request list query parameters
     * @return paged session response
     */
    @GetMapping("/agents/{agentId}")
    public PageResponse<SessionUsage> agent(@PathVariable("agentId") String agentId,
                                            @ModelAttribute UsageQueryRequest request) {
        var snapshot = snapshotStore.current();
        var filter = filter(request.withScope(agentId, null, null, null, null, false));
        var sessions = aggregationService.topSessions(aggregationService.filterCurrent(snapshot.sessions(), filter), Integer.MAX_VALUE).stream()
            .map(FinOpsController::toSessionUsage)
            .toList();
        return page(snapshot.status(), sessions, request.getPage(), request.getSize());
    }

    /**
     * Returns paged user usage rows.
     *
     * @param request list query parameters
     * @return paged user usage response
     */
    @GetMapping("/users")
    public PageResponse<UserUsage> users(@ModelAttribute UsageQueryRequest request) {
        var snapshot = snapshotStore.current();
        var filter = filter(request.withScope(null, null, null, null, null, false));
        return page(snapshot.status(), aggregationService.users(aggregationService.filterCurrent(snapshot.sessions(), filter)), request.getPage(), request.getSize());
    }

    /**
     * Returns paged sessions for one user.
     *
     * @param userId user identifier
     * @param request list query parameters
     * @return paged session response
     */
    @GetMapping("/users/{userId}")
    public PageResponse<SessionUsage> user(@PathVariable("userId") String userId,
                                           @ModelAttribute UsageQueryRequest request) {
        var snapshot = snapshotStore.current();
        var filter = filter(request.withScope(null, userId, null, null, null, false));
        var sessions = aggregationService.topSessions(aggregationService.filterCurrent(snapshot.sessions(), filter), Integer.MAX_VALUE).stream()
            .map(FinOpsController::toSessionUsage)
            .toList();
        return page(snapshot.status(), sessions, request.getPage(), request.getSize());
    }

    /**
     * Returns paged session usage rows.
     *
     * @param request list query parameters
     * @return paged session usage response
     */
    @GetMapping("/sessions")
    public PageResponse<SessionUsage> sessions(@ModelAttribute UsageQueryRequest request) {
        var snapshot = snapshotStore.current();
        var filter = filter(request.withScope(request.getAgentId(), request.getUserId(), request.getSessionType(), null, null, false));
        var sessions = aggregationService.topSessions(aggregationService.filterCurrent(snapshot.sessions(), filter), Integer.MAX_VALUE).stream()
            .map(FinOpsController::toSessionUsage)
            .toList();
        return page(snapshot.status(), sessions, request.getPage(), request.getSize());
    }

    /**
     * Returns one session by its composite identity.
     *
     * @param sessionId session identifier
     * @param userId user identifier
     * @param agentId agent identifier
     * @return session usage record
     */
    @GetMapping("/sessions/{sessionId}")
    public SessionUsage session(@PathVariable("sessionId") String sessionId,
                                @RequestParam("userId") String userId,
                                @RequestParam("agentId") String agentId) {
        SessionUsageRecord session = snapshotStore.current().session(sessionId, userId, agentId);
        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }
        return toSessionUsage(session);
    }

    /**
     * Returns normalized message details for one session.
     *
     * @param sessionId session identifier
     * @param userId user identifier
     * @param agentId agent identifier
     * @return session message response
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public SessionMessagesResponse sessionMessages(@PathVariable("sessionId") String sessionId,
                                                   @RequestParam("userId") String userId,
                                                   @RequestParam("agentId") String agentId) {
        var snapshot = snapshotStore.current();
        SessionUsageRecord session = snapshot.session(sessionId, userId, agentId);
        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }
        List<SessionMessageRecord> messages = snapshot.messages(sessionId, userId, agentId);
        return new SessionMessagesResponse(
            snapshot.status(),
            toSessionUsage(session),
            toMessageStats(messages),
            toCapabilities(messages),
            messages.stream().map(FinOpsController::toMessageDetail).toList()
        );
    }

    /**
     * Returns paged model usage rows.
     *
     * @param request list query parameters
     * @return paged model usage response
     */
    @GetMapping("/models")
    public PageResponse<ModelUsage> models(@ModelAttribute UsageQueryRequest request) {
        var snapshot = snapshotStore.current();
        var filter = filter(request.withScope(null, null, null, null, null, false));
        return page(snapshot.status(), aggregationService.models(aggregationService.filterCurrent(snapshot.sessions(), filter)), request.getPage(), request.getSize());
    }

    /**
     * Triggers a manual snapshot refresh.
     *
     * @return refreshed snapshot status
     */
    @PostMapping("/refresh")
    public SnapshotStatus refresh() {
        return ingestionService.refresh().status();
    }

    private QueryFilter filter(UsageQueryRequest request) {
        return aggregationService.buildFilter(new UsageFilterRequest(
            request.getStartTime(),
            request.getEndTime(),
            request.getAgentId(),
            request.getUserId(),
            request.getSessionType(),
            request.getProviderName(),
            request.getModelName(),
            request.getCompare()
        ));
    }

    private static SessionUsage toSessionUsage(SessionUsageRecord session) {
        return new SessionUsage(
            session.id(),
            session.userId(),
            session.agentId(),
            session.name(),
            session.sessionType(),
            session.createdAt(),
            session.updatedAt(),
            session.totalTokens(),
            session.inputTokens(),
            session.outputTokens(),
            session.scheduleId(),
            session.providerName(),
            session.modelName(),
            session.messageCount(),
            session.userMessageCount(),
            session.assistantMessageCount(),
            session.toolResponseCount(),
            session.label()
        );
    }

    private static SessionMessageDetail toMessageDetail(SessionMessageRecord message) {
        return new SessionMessageDetail(
            message.messageId(),
            message.rowId(),
            message.role(),
            message.createdAt(),
            message.insertedAt(),
            message.tokens(),
            message.contentLength(),
            message.contentPreview(),
            message.contentText(),
            message.contentTruncated(),
            message.toolRequest(),
            message.toolResponse(),
            message.toolName(),
            message.error(),
            message.userVisible(),
            message.agentVisible()
        );
    }

    private static SessionMessageStats toMessageStats(List<SessionMessageRecord> messages) {
        SessionMessageRecord largest = messages.stream()
            .max((left, right) -> Integer.compare(left.contentLength(), right.contentLength()))
            .orElse(null);
        return new SessionMessageStats(
            messages.size(),
            (int) messages.stream().filter(item -> "user".equalsIgnoreCase(item.role())).count(),
            (int) messages.stream().filter(item -> "assistant".equalsIgnoreCase(item.role())).count(),
            (int) messages.stream().filter(SessionMessageRecord::toolRequest).count(),
            (int) messages.stream().filter(SessionMessageRecord::toolResponse).count(),
            (int) messages.stream().filter(item -> item.tokens() != null).count(),
            largest == null ? 0 : largest.contentLength(),
            largest == null ? null : messageRole(largest),
            largest == null ? null : largest.contentPreview()
        );
    }

    private static SessionMessageCapabilities toCapabilities(List<SessionMessageRecord> messages) {
        return new SessionMessageCapabilities(
            messages.stream().anyMatch(item -> item.tokens() != null),
            messages.stream().anyMatch(item -> item.contentPreview() != null && !item.contentPreview().isBlank()),
            messages.stream().anyMatch(item -> item.toolRequest() || item.toolResponse())
        );
    }

    private static String messageRole(SessionMessageRecord message) {
        return message.toolRequest() || message.toolResponse() ? "tool" : message.role();
    }

    private static <T> PageResponse<T> page(SnapshotStatus status, List<T> items, Integer page, Integer size) {
        int safeSize = Math.max(1, Math.min(100, size == null ? 25 : size));
        int totalPages = Math.max(1, (int) Math.ceil((double) items.size() / safeSize));
        int safePage = Math.min(Math.max(1, page == null ? 1 : page), totalPages);
        int from = Math.min((safePage - 1) * safeSize, items.size());
        int to = Math.min(from + safeSize, items.size());
        return new PageResponse<>(status, items.subList(from, to), safePage, safeSize, items.size(), totalPages);
    }

    /**
     * Query parameters accepted by FinOps list and overview endpoints.
     */
    public static class UsageQueryRequest {
        private String startTime;
        private String endTime;
        private String agentId;
        private String userId;
        private String sessionType;
        private String providerName;
        private String modelName;
        private Boolean compare;
        private Integer page = 1;
        private Integer size = 25;

        /**
         * Gets the inclusive range start.
         *
         * @return range start time
         */
        public String getStartTime() {
            return startTime;
        }

        /**
         * Sets the inclusive range start.
         *
         * @param startTime range start time
         */
        public void setStartTime(String startTime) {
            this.startTime = startTime;
        }

        /**
         * Gets the exclusive range end.
         *
         * @return range end time
         */
        public String getEndTime() {
            return endTime;
        }

        /**
         * Sets the exclusive range end.
         *
         * @param endTime range end time
         */
        public void setEndTime(String endTime) {
            this.endTime = endTime;
        }

        /**
         * Gets the agent filter.
         *
         * @return agent identifier
         */
        public String getAgentId() {
            return agentId;
        }

        /**
         * Sets the agent filter.
         *
         * @param agentId agent identifier
         */
        public void setAgentId(String agentId) {
            this.agentId = agentId;
        }

        /**
         * Gets the user filter.
         *
         * @return user identifier
         */
        public String getUserId() {
            return userId;
        }

        /**
         * Sets the user filter.
         *
         * @param userId user identifier
         */
        public void setUserId(String userId) {
            this.userId = userId;
        }

        /**
         * Gets the session type filter.
         *
         * @return session type
         */
        public String getSessionType() {
            return sessionType;
        }

        /**
         * Sets the session type filter.
         *
         * @param sessionType session type
         */
        public void setSessionType(String sessionType) {
            this.sessionType = sessionType;
        }

        /**
         * Gets the provider filter.
         *
         * @return provider name
         */
        public String getProviderName() {
            return providerName;
        }

        /**
         * Sets the provider filter.
         *
         * @param providerName provider name
         */
        public void setProviderName(String providerName) {
            this.providerName = providerName;
        }

        /**
         * Gets the model filter.
         *
         * @return model name
         */
        public String getModelName() {
            return modelName;
        }

        /**
         * Sets the model filter.
         *
         * @param modelName model name
         */
        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        /**
         * Gets whether period comparison is enabled.
         *
         * @return comparison flag
         */
        public Boolean getCompare() {
            return compare;
        }

        /**
         * Sets whether period comparison is enabled.
         *
         * @param compare comparison flag
         */
        public void setCompare(Boolean compare) {
            this.compare = compare;
        }

        /**
         * Gets the requested page number.
         *
         * @return page number
         */
        public Integer getPage() {
            return page;
        }

        /**
         * Sets the requested page number.
         *
         * @param page page number
         */
        public void setPage(Integer page) {
            this.page = page;
        }

        /**
         * Gets the requested page size.
         *
         * @return page size
         */
        public Integer getSize() {
            return size;
        }

        /**
         * Sets the requested page size.
         *
         * @param size page size
         */
        public void setSize(Integer size) {
            this.size = size;
        }

        private UsageQueryRequest withScope(String agentId,
                                            String userId,
                                            String sessionType,
                                            String providerName,
                                            String modelName,
                                            Boolean compare) {
            this.agentId = agentId;
            this.userId = userId;
            this.sessionType = sessionType;
            this.providerName = providerName;
            this.modelName = modelName;
            this.compare = compare;
            return this;
        }
    }
}
