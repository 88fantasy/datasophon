/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.datasophon.api.lineage;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionOperations;

import com.fasterxml.jackson.databind.JsonNode;
import com.mysql.cj.jdbc.exceptions.MySQLTransactionRollbackException;

/** Authoritative OpenLineage write path. It never reads or mutates the in-memory graph. */
public final class LineageIngestService implements LineageIngestOperations {

    private static final int MAX_DEADLOCK_RETRIES = 3;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionOperations writeTransaction;
    private final LineageEventDecoder eventDecoder;
    private final CanonicalNameResolver canonicalNameResolver;
    private final StructuralHashCalculator hashCalculator;
    private final WatermarkExtractor watermarkExtractor;
    private final ApplicationEventPublisher eventPublisher;
    private final IngestMetrics metrics;
    private final Clock clock;
    private final Sleeper sleeper;

    public LineageIngestService(JdbcTemplate jdbcTemplate, TransactionOperations writeTransaction,
                                LineageEventDecoder eventDecoder, CanonicalNameResolver canonicalNameResolver,
                                StructuralHashCalculator hashCalculator, WatermarkExtractor watermarkExtractor,
                                ApplicationEventPublisher eventPublisher, IngestMetrics metrics) {
        this(jdbcTemplate, writeTransaction, eventDecoder, canonicalNameResolver, hashCalculator, watermarkExtractor,
                eventPublisher, metrics, Clock.systemUTC(), Thread::sleep);
    }

    LineageIngestService(JdbcTemplate jdbcTemplate, TransactionOperations writeTransaction,
                         LineageEventDecoder eventDecoder, CanonicalNameResolver canonicalNameResolver,
                         StructuralHashCalculator hashCalculator, WatermarkExtractor watermarkExtractor,
                         ApplicationEventPublisher eventPublisher, IngestMetrics metrics, Clock clock, Sleeper sleeper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.writeTransaction = Objects.requireNonNull(writeTransaction, "writeTransaction");
        this.eventDecoder = Objects.requireNonNull(eventDecoder, "eventDecoder");
        this.canonicalNameResolver = Objects.requireNonNull(canonicalNameResolver, "canonicalNameResolver");
        this.hashCalculator = Objects.requireNonNull(hashCalculator, "hashCalculator");
        this.watermarkExtractor = Objects.requireNonNull(watermarkExtractor, "watermarkExtractor");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    @Override
    public IngestResult ingest(long clusterId, JsonNode payload) {
        if (clusterId <= 0) {
            throw new IllegalArgumentException("clusterId must be positive");
        }
        Instant receivedAt = clock.instant();
        DecodedLineageEvent event = eventDecoder.decode(payload);
        WatermarkExtractor.Extraction watermark = watermarkExtractor.extract(event, receivedAt);
        PreparedEvent prepared = prepare(event, receivedAt, watermark);

        IngestResult result = executeWithDeadlockRetry(() -> Objects.requireNonNull(writeTransaction.execute(status -> persist(clusterId, prepared)),
                "ingest transaction returned null"));
        recordMetrics(result);
        return result;
    }

    private PreparedEvent prepare(DecodedLineageEvent event, Instant receivedAt,
                                  WatermarkExtractor.Extraction watermark) {
        if (!event.participatesInStructure() || event.inputs().isEmpty() && event.outputs().isEmpty()) {
            return new PreparedEvent(event, receivedAt, watermark, List.of(), List.of(), false);
        }
        List<ResolvedDataset> inputs = resolve(event.inputs());
        List<ResolvedDataset> outputs = resolve(event.outputs());
        boolean resolved = inputs.size() == event.inputs().size() && outputs.size() == event.outputs().size();
        return new PreparedEvent(event, receivedAt, watermark, inputs, outputs, resolved);
    }

    private List<ResolvedDataset> resolve(List<DatasetIdentity> datasets) {
        List<ResolvedDataset> resolved = new ArrayList<>(datasets.size());
        for (DatasetIdentity dataset : datasets) {
            Optional<ResolvedDataset> value = canonicalNameResolver.resolve(dataset);
            if (value.isEmpty()) {
                return resolved;
            }
            resolved.add(value.orElseThrow());
        }
        return resolved;
    }

    private IngestResult persist(long clusterId, PreparedEvent prepared) {
        DecodedLineageEvent event = prepared.event();
        int eventRows = jdbcTemplate.update(
                """
                        INSERT IGNORE INTO t_ddh_lineage_event
                            (producer, run_id, event_type, run_started_at, received_at, status)
                        VALUES (?, ?, ?, ?, ?, 'RECEIVED')
                        """,
                event.producer(), event.runId(), event.eventType(), runStartedAt(prepared), timestamp(prepared.receivedAt()));
        if (eventRows == 0) {
            return IngestResult.of(Status.DUPLICATE);
        }

        if (!event.participatesInStructure()) {
            insertParseLog(null, null, "IGNORED_EVENT", event.eventType() + " does not update structure");
            updateEventStatus(event, "IGNORED");
            return IngestResult.of(Status.IGNORED_EVENT);
        }
        if (event.inputs().isEmpty() && event.outputs().isEmpty()) {
            insertParseLog(null, null, "SKIPPED_EMPTY", "inputs and outputs are both empty");
            updateEventStatus(event, "SKIPPED");
            return IngestResult.of(Status.SKIPPED_EMPTY);
        }
        if (!prepared.resolved()) {
            insertParseLog(null, null, "UNRESOLVED_DATASET", "dataset namespace/name could not be resolved");
            updateEventStatus(event, "SKIPPED");
            return IngestResult.of(Status.SKIPPED_UNRESOLVED);
        }

        long jobId = claimJob(clusterId, event.engine(), event.jobName());
        jdbcTemplate.update(
                """
                        UPDATE t_ddh_lineage_event SET job_id = ?
                        WHERE producer = ? AND run_id = ? AND event_type = ?
                        """,
                jobId, event.producer(), event.runId(), event.eventType());
        JobState job = lockJob(jobId);

        if (prepared.watermark().degraded()) {
            insertParseLog(jobId, null, "DEGRADED_WATERMARK", "received_at used as ordering watermark");
        }
        if (job.currentWatermark() != null && prepared.watermark().epochMillis() <= job.currentWatermark()) {
            insertParseLog(jobId, null, "LATE_EVENT", "event watermark is not newer than current watermark");
            updateEventStatus(event, "LATE");
            return new IngestResult(Status.LATE_EVENT, jobId, null, 0, 0);
        }

        String structuralHash = hashCalculator.structuralHash(prepared.inputs(), prepared.outputs());
        Map<String, ResolvedDataset> datasets = sortedDistinct(prepared.inputs(), prepared.outputs());
        Map<String, Long> nodeIds = upsertNodes(clusterId, datasets.values(), prepared.receivedAt());
        long touchedNodes = nodeIds.size();

        if (structuralHash.equals(job.currentStructuralHash())) {
            jdbcTemplate.update(
                    "UPDATE t_ddh_data_job SET current_watermark = ? WHERE id = ?",
                    prepared.watermark().epochMillis(), jobId);
            updateEventStatus(event, "UNCHANGED");
            return new IngestResult(Status.UNCHANGED, jobId, currentVersion(jobId), 0, touchedNodes);
        }

        int nextVersion = currentVersion(jobId) + 1;
        String definitionText =
                hashCalculator.definitionText(event.sqlQuery(), prepared.inputs(), prepared.outputs());
        String contentHash = hashCalculator.contentHash(definitionText);

        // Fixed lock order:
        // t_ddh_data_job(job_id) -> t_ddh_lineage_node(canonical_name sorted)
        // -> t_ddh_lineage_edge / t_ddh_data_job_definition -> t_ddh_lineage_generation.
        jdbcTemplate.update("UPDATE t_ddh_lineage_edge SET is_current = 0 WHERE job_id = ? AND is_current = 1",
                jobId);
        jdbcTemplate.update(
                """
                        INSERT INTO t_ddh_data_job_definition (job_id, version, definition_text, content_hash)
                        VALUES (?, ?, ?, ?)
                        """,
                jobId, nextVersion, definitionText, contentHash);
        long edgeRows = insertEdges(jobId, nextVersion, event.flowType(), prepared.inputs(), prepared.outputs(),
                nodeIds);
        jdbcTemplate.update(
                """
                        UPDATE t_ddh_data_job
                        SET current_structural_hash = ?, current_watermark = ?
                        WHERE id = ?
                        """,
                structuralHash, prepared.watermark().epochMillis(), jobId);
        // 代际计数器每集群一行，且不再有种子行（L3/D5），因此必须能自建行。
        jdbcTemplate.update(
                """
                        INSERT INTO t_ddh_lineage_generation (cluster_id, generation) VALUES (?, 1)
                        ON DUPLICATE KEY UPDATE generation = generation + 1
                        """,
                clusterId);
        updateEventStatus(event, "CHANGED");
        eventPublisher.publishEvent(new LineageStructureChangedEvent(clusterId, jobId, nextVersion));
        return new IngestResult(Status.CHANGED, jobId, nextVersion, edgeRows, touchedNodes);
    }

    private long claimJob(long clusterId, String engine, String jobName) {
        jdbcTemplate.update(
                """
                        INSERT INTO t_ddh_data_job (cluster_id, engine, job_name, job_type, state)
                        VALUES (?, ?, ?, 'UNKNOWN', 'UNKNOWN')
                        ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)
                        """,
                clusterId, engine, jobName);
        return Objects.requireNonNull(jdbcTemplate.queryForObject(
                "SELECT id FROM t_ddh_data_job WHERE cluster_id = ? AND engine = ? AND job_name = ?",
                Long.class, clusterId, engine, jobName));
    }

    private JobState lockJob(long jobId) {
        long startedAt = System.nanoTime();
        JobState job = Objects.requireNonNull(jdbcTemplate.queryForObject(
                """
                        SELECT current_structural_hash, current_watermark
                        FROM t_ddh_data_job
                        WHERE id = ?
                        FOR UPDATE
                        """,
                (resultSet, rowNumber) -> {
                    long watermark = resultSet.getLong("current_watermark");
                    return new JobState(
                            resultSet.getString("current_structural_hash"),
                            resultSet.wasNull() ? null : watermark);
                },
                jobId));
        metrics.lockWait(System.nanoTime() - startedAt);
        return job;
    }

    private Map<String, Long> upsertNodes(long clusterId, Collection<ResolvedDataset> datasets, Instant seenAt) {
        Map<String, Long> nodeIds = new LinkedHashMap<>();
        for (ResolvedDataset dataset : datasets) {
            jdbcTemplate.update(
                    """
                            INSERT INTO t_ddh_lineage_node
                                (cluster_id, connector, catalog_name, database_name, table_name, canonical_name,
                                 dw_layer, first_seen, last_seen)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) AS new
                            ON DUPLICATE KEY UPDATE
                                last_seen = GREATEST(t_ddh_lineage_node.last_seen, new.last_seen),
                                id = LAST_INSERT_ID(id)
                            """,
                    clusterId, dataset.connector(), dataset.catalogName(), dataset.databaseName(),
                    dataset.tableName(), dataset.canonicalName(), dataset.dwLayer(), timestamp(seenAt),
                    timestamp(seenAt));
            // 回查必须带 cluster_id：唯一键是 (cluster_id, canonical_name)，只按 canonical_name
            // 查会在跨集群同名表时静默拿到别的集群的 node id（L3 踩坑点 P1）。
            long nodeId = Objects.requireNonNull(jdbcTemplate.queryForObject(
                    "SELECT id FROM t_ddh_lineage_node WHERE cluster_id = ? AND canonical_name = ?",
                    Long.class, clusterId, dataset.canonicalName()));
            nodeIds.put(dataset.canonicalName(), nodeId);
        }
        return nodeIds;
    }

    private long insertEdges(long jobId, int definitionVersion, String flowType, List<ResolvedDataset> inputs,
                             List<ResolvedDataset> outputs, Map<String, Long> nodeIds) {
        long count = 0;
        for (ResolvedDataset input : distinctByCanonicalName(inputs).values()) {
            for (ResolvedDataset output : distinctByCanonicalName(outputs).values()) {
                count += jdbcTemplate.update(
                        """
                                INSERT INTO t_ddh_lineage_edge
                                    (job_id, definition_version, src_node_id, dst_node_id, flow_type, is_current)
                                VALUES (?, ?, ?, ?, ?, 1)
                                """,
                        jobId, definitionVersion, nodeIds.get(input.canonicalName()),
                        nodeIds.get(output.canonicalName()), flowType);
            }
        }
        return count;
    }

    private int currentVersion(long jobId) {
        Integer version = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(version), 0) FROM t_ddh_data_job_definition WHERE job_id = ?",
                Integer.class, jobId);
        return Objects.requireNonNull(version, "definition version");
    }

    private void insertParseLog(Long jobId, Integer definitionVersion, String status, String message) {
        jdbcTemplate.update(
                """
                        INSERT INTO t_ddh_lineage_parse_log (job_id, definition_version, status, message)
                        VALUES (?, ?, ?, ?)
                        """,
                jobId, definitionVersion, status, message);
    }

    private void updateEventStatus(DecodedLineageEvent event, String status) {
        jdbcTemplate.update(
                """
                        UPDATE t_ddh_lineage_event SET status = ?
                        WHERE producer = ? AND run_id = ? AND event_type = ?
                        """,
                status, event.producer(), event.runId(), event.eventType());
    }

    <T> T executeWithDeadlockRetry(TransactionAttempt<T> transaction) {
        int retry = 0;
        while (true) {
            try {
                return transaction.execute();
            } catch (RuntimeException e) {
                if (!isDeadlock(e) || retry >= MAX_DEADLOCK_RETRIES) {
                    throw e;
                }
                long backoff = jitteredBackoff(retry++);
                metrics.deadlockRetry(retry, backoff);
                try {
                    sleeper.sleep(backoff);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted during lineage deadlock retry", interrupted);
                }
            }
        }
    }

    private void recordMetrics(IngestResult result) {
        metrics.eventTotal();
        if (result.status() == Status.CHANGED) {
            metrics.structureChangeTotal();
        }
        if (result.edgeRowsWritten() > 0) {
            metrics.edgeRowsWritten(result.edgeRowsWritten());
        }
        if (result.lastSeenRowsUpdated() > 0) {
            metrics.lastSeenRowsUpdated(result.lastSeenRowsUpdated());
        }
    }

    private static boolean isDeadlock(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof CannotAcquireLockException
                    || current instanceof DeadlockLoserDataAccessException
                    || current instanceof MySQLTransactionRollbackException
                    || current instanceof SQLException sqlException && "40001".equals(sqlException.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private static long jitteredBackoff(int retry) {
        long base = 50L << retry;
        long minimum = base * 80 / 100;
        long maximum = base * 120 / 100;
        return ThreadLocalRandom.current().nextLong(minimum, maximum + 1);
    }

    private static Timestamp runStartedAt(PreparedEvent prepared) {
        if (prepared.watermark().source() == WatermarkExtractor.Source.RECEIVED_AT) {
            return null;
        }
        return new Timestamp(prepared.watermark().epochMillis());
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    @SafeVarargs
    private static Map<String, ResolvedDataset> sortedDistinct(List<ResolvedDataset>... groups) {
        Map<String, ResolvedDataset> result = new TreeMap<>();
        for (List<ResolvedDataset> group : groups) {
            group.forEach(dataset -> result.putIfAbsent(dataset.canonicalName(), dataset));
        }
        return result;
    }

    private static Map<String, ResolvedDataset> distinctByCanonicalName(List<ResolvedDataset> datasets) {
        Map<String, ResolvedDataset> result = new TreeMap<>();
        datasets.forEach(dataset -> result.putIfAbsent(dataset.canonicalName(), dataset));
        return result;
    }

    public enum Status {
        DUPLICATE,
        IGNORED_EVENT,
        SKIPPED_EMPTY,
        SKIPPED_UNRESOLVED,
        LATE_EVENT,
        UNCHANGED,
        CHANGED
    }

    public record IngestResult(Status status, Long jobId, Integer definitionVersion, long edgeRowsWritten,
            long lastSeenRowsUpdated) {

        static IngestResult of(Status status) {
            return new IngestResult(status, null, null, 0, 0);
        }
    }

    private record PreparedEvent(DecodedLineageEvent event, Instant receivedAt,
                                 WatermarkExtractor.Extraction watermark, List<ResolvedDataset> inputs, List<ResolvedDataset> outputs,
                                 boolean resolved) {
    }

    private record JobState(String currentStructuralHash, Long currentWatermark) {
    }

    @FunctionalInterface
    interface TransactionAttempt<T> {

        T execute();
    }

    @FunctionalInterface
    interface Sleeper {

        void sleep(long millis) throws InterruptedException;
    }
}
