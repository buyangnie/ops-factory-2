package com.huawei.opsfactory.finops.store;

import com.huawei.opsfactory.finops.model.FinOpsModels.SessionMessageRecord;
import com.huawei.opsfactory.finops.model.FinOpsModels.SessionUsageRecord;
import com.huawei.opsfactory.finops.model.FinOpsModels.SnapshotStatus;
import com.huawei.opsfactory.finops.model.FinOpsModels.UsageSnapshotPayload;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Stores the latest FinOps snapshot in memory for API reads.
 *
 * @since 2026-05-28
 */
@Component
public class FinOpsSnapshotStore {

    private final AtomicReference<Snapshot> current = new AtomicReference<>(
        new Snapshot(List.of(), List.of(), new SnapshotStatus("empty", null, 0, 0, 0, null), "")
    );

    /**
     * Returns the current in-memory snapshot.
     *
     * @return current snapshot
     */
    public Snapshot current() {
        return current.get();
    }

    /**
     * Replaces the current snapshot from a successful gateway payload.
     *
     * @param scanResult gateway snapshot payload
     * @return stored snapshot
     */
    public Snapshot update(UsageSnapshotPayload scanResult) {
        Snapshot snapshot = new Snapshot(
            List.copyOf(scanResult.sessions()),
            List.copyOf(scanResult.messages()),
            new SnapshotStatus(
                scanResult.skippedDbCount() > 0 ? "partial" : "ready",
                Instant.now(),
                scanResult.sourceDbCount(),
                scanResult.skippedDbCount(),
                scanResult.sessions().size(),
                scanResult.lastError()
            ),
            scanResult.dataSource()
        );
        current.set(snapshot);
        return snapshot;
    }

    /**
     * Marks the current snapshot stale after a refresh failure.
     *
     * @param ex refresh exception
     * @return stale snapshot retaining previous data
     */
    public Snapshot markFailed(Exception ex) {
        Snapshot previous = current.get();
        Snapshot failed = new Snapshot(
            previous.sessions(),
            previous.messages(),
            new SnapshotStatus(
                "stale",
                previous.status().lastRefreshedAt(),
                previous.status().sourceDbCount(),
                previous.status().skippedDbCount(),
                previous.status().sessionCount(),
                ex.getMessage()
            ),
            previous.dataSource()
        );
        current.set(failed);
        return failed;
    }

    /**
     * Immutable FinOps snapshot with session lookup indexes.
     *
     * @param sessions session usage records
     * @param messages session message records
     * @param status snapshot status
     * @param dataSource snapshot source description
     * @param sessionsByIdentity session identity index
     * @param messagesByIdentity message identity index
     */
    public record Snapshot(
        List<SessionUsageRecord> sessions,
        List<SessionMessageRecord> messages,
        SnapshotStatus status,
        String dataSource,
        Map<SessionKey, SessionUsageRecord> sessionsByIdentity,
        Map<SessionKey, List<SessionMessageRecord>> messagesByIdentity
    ) {
        /**
         * Builds a snapshot and derives lookup indexes.
         *
         * @param sessions session usage records
         * @param messages session message records
         * @param status snapshot status
         * @param dataSource snapshot source description
         */
        public Snapshot(List<SessionUsageRecord> sessions,
                        List<SessionMessageRecord> messages,
                        SnapshotStatus status,
                        String dataSource) {
            this(
                sessions,
                messages,
                status,
                dataSource,
                sessions.stream().collect(Collectors.toMap(Snapshot::sessionKey, item -> item, (first, ignored) -> first)),
                messages.stream().collect(Collectors.groupingBy(Snapshot::messageKey))
            );
        }

        /**
         * Looks up one session by composite identity.
         *
         * @param sessionId session identifier
         * @param userId user identifier
         * @param agentId agent identifier
         * @return matching session, or null when absent
         */
        public SessionUsageRecord session(String sessionId, String userId, String agentId) {
            return sessionsByIdentity.get(new SessionKey(sessionId, userId, agentId));
        }

        /**
         * Looks up messages for one session identity.
         *
         * @param sessionId session identifier
         * @param userId user identifier
         * @param agentId agent identifier
         * @return matching messages
         */
        public List<SessionMessageRecord> messages(String sessionId, String userId, String agentId) {
            return messagesByIdentity.getOrDefault(new SessionKey(sessionId, userId, agentId), List.of());
        }

        private static SessionKey sessionKey(SessionUsageRecord session) {
            return new SessionKey(session.id(), session.userId(), session.agentId());
        }

        private static SessionKey messageKey(SessionMessageRecord message) {
            return new SessionKey(message.sessionId(), message.userId(), message.agentId());
        }
    }

    private record SessionKey(String sessionId, String userId, String agentId) {
    }
}
