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

package com.datasophon.api.controller.v2;

import com.datasophon.api.controller.ApiController;
import com.datasophon.api.lineage.LineageGenerationReader;
import com.datasophon.api.lineage.LineageGraphQuery;
import com.datasophon.api.lineage.LineageGraphQuery.Direction;
import com.datasophon.api.lineage.LineageGraphQuery.GraphData;
import com.datasophon.api.lineage.LineageGraphQuery.InvalidExpansionTokenException;
import com.datasophon.api.lineage.LineageGraphQuery.NodeNotFoundException;
import com.datasophon.api.lineage.LineageGraphQuery.StaleExpansionTokenException;
import com.datasophon.api.lineage.LineageGraphSnapshot;
import com.datasophon.api.lineage.LineageGraphSnapshotHolder;
import com.datasophon.api.lineage.LineageIngestOperations;
import com.datasophon.api.lineage.LineageIngestService.IngestResult;
import com.datasophon.api.lineage.LineageJobDetailReader;
import com.datasophon.api.lineage.LineageJobDetailReader.JobDetail;
import com.datasophon.api.lineage.LineageLeaseGuard;
import com.datasophon.api.lineage.LineageRebuildCoordinator;
import com.datasophon.api.lineage.NodeMeta;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;

/** OpenLineage-compatible ingest and bounded snapshot query endpoints. */
@RestController
@RequestMapping("/v2")
public class LineageV2Controller extends ApiController {

    private static final Logger logger = LoggerFactory.getLogger(LineageV2Controller.class);

    private final LineageIngestOperations ingestService;
    private final LineageLeaseGuard leaseGuard;
    private final LineageGraphSnapshotHolder snapshotHolder;
    private final LineageRebuildCoordinator coordinator;
    private final LineageGenerationReader generationReader;
    private final LineageGraphQuery graphQuery;
    private final LineageJobDetailReader jobDetailReader;
    private final long staleThresholdSeconds;
    private final long sourceLaggingThresholdSeconds;
    private final String ingestToken;

    public LineageV2Controller(
                               LineageIngestOperations ingestService,
                               LineageLeaseGuard leaseGuard,
                               LineageGraphSnapshotHolder snapshotHolder,
                               LineageRebuildCoordinator coordinator,
                               LineageGenerationReader generationReader,
                               LineageGraphQuery graphQuery,
                               LineageJobDetailReader jobDetailReader,
                               @Value("${datasophon.lineage.stale-threshold-seconds:600}") long staleThresholdSeconds,
                               @Value("${datasophon.lineage.source-lagging-threshold-seconds:1800}") long sourceLaggingThresholdSeconds,
                               @Value("${datasophon.lineage.ingest-token:}") String ingestToken) {
        this.ingestService = ingestService;
        this.leaseGuard = leaseGuard;
        this.snapshotHolder = snapshotHolder;
        this.coordinator = coordinator;
        this.generationReader = generationReader;
        this.graphQuery = graphQuery;
        this.jobDetailReader = jobDetailReader;
        if (staleThresholdSeconds <= 0) {
            throw new IllegalArgumentException("staleThresholdSeconds must be positive");
        }
        this.staleThresholdSeconds = staleThresholdSeconds;
        if (sourceLaggingThresholdSeconds <= 0) {
            throw new IllegalArgumentException("sourceLaggingThresholdSeconds must be positive");
        }
        this.sourceLaggingThresholdSeconds = sourceLaggingThresholdSeconds;
        this.ingestToken = ingestToken;
    }

    /**
     * L2: OpenLineage ingest 是机器对机器端点，走独立的共享 Bearer token 校验，不接普通用户会话
     * （{@code /v2/lineage} 已在 {@code AppConfiguration} 中排除了登录/CSRF 拦截器）。
     * Token 未配置时默认拒绝所有请求（fail closed），与 {@code InternalMetaController} 的
     * {@code X-Internal-Token} 校验同一套姿势，只是凭证来自标准 {@code Authorization: Bearer}
     * 头 —— 这是 Gravitino {@code LineageHttpSink} 的 {@code authType=apiKey} 实际发送的格式。
     */
    @PostMapping("/lineage")
    public IngestResult ingest(
                               @RequestHeader(name = "Authorization", required = false) String authorization,
                               @RequestParam long clusterId,
                               @RequestBody JsonNode payload) {
        requireValidIngestToken(authorization);
        leaseGuard.requireOwner();
        try {
            return ingestService.ingest(clusterId, payload);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    private void requireValidIngestToken(String authorization) {
        if (StringUtils.isBlank(ingestToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "datasophon.lineage.ingest-token is not configured");
        }
        String bearerPrefix = "Bearer ";
        if (authorization == null || !authorization.startsWith(bearerPrefix)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "missing bearer token");
        }
        String presented = authorization.substring(bearerPrefix.length());
        boolean matches = MessageDigest.isEqual(
                ingestToken.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid bearer token");
        }
    }

    @GetMapping("/lineage/readiness")
    public ResponseEntity<LeaseReadiness> readiness() {
        boolean owner = leaseGuard.isOwner();
        LeaseReadiness body = owner
                ? new LeaseReadiness(true, "UP", "Lineage Master lease is held")
                : new LeaseReadiness(false, "DOWN", LineageLeaseGuard.UNAVAILABLE_MESSAGE);
        return ResponseEntity.status(owner ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    @GetMapping("/lineage/graph")
    public LineageQueryResponse graph(
                                      @RequestParam long clusterId,
                                      @RequestParam long rootNodeId,
                                      @RequestParam(defaultValue = "2") int depth,
                                      @RequestParam(defaultValue = "both") String direction,
                                      @RequestParam(required = false) String expand) {
        leaseGuard.requireOwner();
        validateDepth(depth);
        Direction parsedDirection = parseDirection(direction);
        LineageGraphSnapshot snapshot = requireSnapshot(clusterId);
        requireNode(snapshot, rootNodeId);
        GraphData data;
        try {
            data = expand == null
                    ? graphQuery.query(snapshot, rootNodeId, depth, parsedDirection)
                    : graphQuery.expand(snapshot, expand);
        } catch (StaleExpansionTokenException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        } catch (InvalidExpansionTokenException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (NodeNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
        return response(clusterId, snapshot, data);
    }

    @GetMapping("/lineage/overview")
    public LineageQueryResponse overview(@RequestParam long clusterId) {
        leaseGuard.requireOwner();
        LineageGraphSnapshot snapshot = requireSnapshot(clusterId);
        return response(clusterId, snapshot, graphQuery.overview(snapshot));
    }

    /** 表清单分页；读的是与图同一份快照，因此列表可点进去的表在图里必然存在。 */
    @GetMapping("/lineage/tables")
    public LineageQueryResponse tables(
                                       @RequestParam long clusterId,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int size,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String layer,
                                       @RequestParam(required = false) String connector,
                                       @RequestParam(required = false) String database) {
        leaseGuard.requireOwner();
        LineageGraphSnapshot snapshot = requireSnapshot(clusterId);
        try {
            return response(clusterId, snapshot,
                    graphQuery.list(snapshot, keyword, layer, connector, database, page, size));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @GetMapping("/lineage/table/{id}")
    public LineageQueryResponse table(@RequestParam long clusterId, @PathVariable long id) {
        leaseGuard.requireOwner();
        LineageGraphSnapshot snapshot = requireSnapshot(clusterId);
        NodeMeta table = graphQuery.table(snapshot, id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "lineage node " + id + " was not found"));
        return response(clusterId, snapshot, table);
    }

    /**
     * 作业详情：唯一直查数据库的查询端点 —— 作业台账不在内存快照里（L3/D8）。
     *
     * <p>必须校验作业归属集群：否则任何人都能用别的集群的 jobId 读到其作业名与外部地址。</p>
     */
    @GetMapping("/lineage/job/{id}")
    public JobDetail job(@RequestParam long clusterId, @PathVariable long id) {
        leaseGuard.requireOwner();
        return jobDetailReader.read(clusterId, id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "lineage job " + id + " was not found"));
    }

    @GetMapping("/lineage/impact")
    public LineageQueryResponse impact(
                                       @RequestParam long clusterId,
                                       @RequestParam long rootNodeId,
                                       @RequestParam(defaultValue = "2") int depth) {
        leaseGuard.requireOwner();
        validateDepth(depth);
        LineageGraphSnapshot snapshot = requireSnapshot(clusterId);
        requireNode(snapshot, rootNodeId);
        QueryContext context = queryContext(clusterId, snapshot);
        if (context.snapshot().stale()) {
            logger.warn(
                    "Rejecting stale lineage impact query: clusterId={}, rootNodeId={}, generation={}, targetGeneration={}",
                    clusterId,
                    rootNodeId,
                    context.snapshot().generation(),
                    context.snapshot().targetGeneration());
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "lineage snapshot is stale; impact analysis is unavailable");
        }
        GraphData data = graphQuery.query(snapshot, rootNodeId, depth, Direction.DOWNSTREAM);
        return context.response(data);
    }

    @PostMapping("/lineage/rebuild")
    public ResponseEntity<RebuildAccepted> rebuild(@RequestParam long clusterId) {
        leaseGuard.requireOwner();
        coordinator.requestRebuild(clusterId, LineageRebuildCoordinator.Trigger.MANUAL);
        return ResponseEntity.accepted().body(new RebuildAccepted(coordinator.currentGeneration(clusterId)));
    }

    private LineageGraphSnapshot requireSnapshot(long clusterId) {
        return snapshotHolder.getForQuery(clusterId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "lineage snapshot is not ready"));
    }

    private static void requireNode(LineageGraphSnapshot snapshot, long rootNodeId) {
        if (!snapshot.graph().nodes().contains(rootNodeId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "lineage node " + rootNodeId + " was not found");
        }
    }

    private static void validateDepth(int depth) {
        if (depth < 1 || depth > 5) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "depth must be between 1 and 5");
        }
    }

    private static Direction parseDirection(String direction) {
        try {
            return Direction.fromRequest(direction);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    private LineageQueryResponse response(long clusterId, LineageGraphSnapshot snapshot, Object data) {
        return queryContext(clusterId, snapshot).response(data);
    }

    private QueryContext queryContext(long clusterId, LineageGraphSnapshot snapshot) {
        long observedDbGeneration = generationReader.readCurrentGeneration(clusterId);
        long ageSeconds = Math.max(
                0,
                Duration.between(snapshot.meta().builtAt(), Instant.now()).getSeconds());
        Optional<Throwable> rebuildError = coordinator.lastRebuildError(clusterId);
        String lastRebuildError = rebuildError
                .map(LineageV2Controller::errorMessage)
                .orElse(null);
        boolean stale = snapshot.generation() < observedDbGeneration
                || rebuildError.isPresent()
                || ageSeconds > staleThresholdSeconds;
        SnapshotFreshness freshness = new SnapshotFreshness(
                snapshot.generation(),
                observedDbGeneration,
                snapshot.meta().builtAt(),
                ageSeconds,
                stale,
                lastRebuildError);
        return new QueryContext(freshness, sourceFreshness(clusterId));
    }

    /**
     * 采集侧新鲜度：最后一次收到血缘事件的时间。
     *
     * <p>与快照新鲜度是两件独立的事 —— 快照可能刚重建完（不 stale），但采集侧已经断流几小时。
     * 读取失败不能让查询整体失败，降级为 {@code UNKNOWN}。</p>
     */
    private SourceFreshness sourceFreshness(long clusterId) {
        Instant lastEventReceivedAt;
        try {
            lastEventReceivedAt = jobDetailReader.readLastEventReceivedAt(clusterId).orElse(null);
        } catch (RuntimeException e) {
            logger.warn("Failed to read lineage source freshness for cluster {}", clusterId, e);
            return new SourceFreshness(null, "UNKNOWN");
        }
        if (lastEventReceivedAt == null) {
            return new SourceFreshness(null, "NO_DATA");
        }
        long lagSeconds = Math.max(0, Duration.between(lastEventReceivedAt, Instant.now()).getSeconds());
        return new SourceFreshness(
                lastEventReceivedAt,
                lagSeconds > sourceLaggingThresholdSeconds ? "LAGGING" : "OK");
    }

    private static String errorMessage(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    public record LeaseReadiness(boolean owner, String status, String message) {
    }

    public record RebuildAccepted(long generation) {
    }

    public record LineageQueryResponse(Object data, SnapshotFreshness snapshot, SourceFreshness sourceFreshness) {
    }

    public record SnapshotFreshness(long generation, long targetGeneration, Instant builtAt, long ageSeconds,
                                    boolean stale, String lastRebuildError) {
    }

    public record SourceFreshness(Instant lastEventReceivedAt, String status) {
    }

    private record QueryContext(SnapshotFreshness snapshot, SourceFreshness source) {

        private QueryContext {
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(source, "source");
        }

        private LineageQueryResponse response(Object data) {
            return new LineageQueryResponse(data, snapshot, source);
        }
    }
}
