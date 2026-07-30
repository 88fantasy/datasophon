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
import com.datasophon.api.lineage.LineageLeaseGuard;
import com.datasophon.api.lineage.LineageRebuildCoordinator;
import com.datasophon.api.lineage.NodeMeta;

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
    private final long staleThresholdSeconds;

    public LineageV2Controller(
                               LineageIngestOperations ingestService,
                               LineageLeaseGuard leaseGuard,
                               LineageGraphSnapshotHolder snapshotHolder,
                               LineageRebuildCoordinator coordinator,
                               LineageGenerationReader generationReader,
                               LineageGraphQuery graphQuery,
                               @Value("${datasophon.lineage.stale-threshold-seconds:600}") long staleThresholdSeconds) {
        this.ingestService = ingestService;
        this.leaseGuard = leaseGuard;
        this.snapshotHolder = snapshotHolder;
        this.coordinator = coordinator;
        this.generationReader = generationReader;
        this.graphQuery = graphQuery;
        if (staleThresholdSeconds <= 0) {
            throw new IllegalArgumentException("staleThresholdSeconds must be positive");
        }
        this.staleThresholdSeconds = staleThresholdSeconds;
    }

    // TODO L2: 接 Gravitino 时补共享 token 校验。
    @PostMapping("/lineage")
    public IngestResult ingest(@RequestParam long clusterId, @RequestBody JsonNode payload) {
        leaseGuard.requireOwner();
        try {
            return ingestService.ingest(clusterId, payload);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
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
                                      @RequestParam long rootNodeId,
                                      @RequestParam(defaultValue = "2") int depth,
                                      @RequestParam(defaultValue = "both") String direction,
                                      @RequestParam(required = false) String expand) {
        leaseGuard.requireOwner();
        validateDepth(depth);
        Direction parsedDirection = parseDirection(direction);
        LineageGraphSnapshot snapshot = requireSnapshot();
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
        return response(snapshot, data);
    }

    @GetMapping("/lineage/overview")
    public LineageQueryResponse overview() {
        leaseGuard.requireOwner();
        LineageGraphSnapshot snapshot = requireSnapshot();
        return response(snapshot, graphQuery.overview(snapshot));
    }

    @GetMapping("/lineage/table/{id}")
    public LineageQueryResponse table(@PathVariable long id) {
        leaseGuard.requireOwner();
        LineageGraphSnapshot snapshot = requireSnapshot();
        NodeMeta table = graphQuery.table(snapshot, id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "lineage node " + id + " was not found"));
        return response(snapshot, table);
    }

    @GetMapping("/lineage/impact")
    public LineageQueryResponse impact(
                                       @RequestParam long rootNodeId,
                                       @RequestParam(defaultValue = "2") int depth) {
        leaseGuard.requireOwner();
        validateDepth(depth);
        LineageGraphSnapshot snapshot = requireSnapshot();
        requireNode(snapshot, rootNodeId);
        QueryContext context = queryContext(snapshot);
        if (context.snapshot().stale()) {
            logger.warn(
                    "Rejecting stale lineage impact query: rootNodeId={}, generation={}, targetGeneration={}",
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
    public ResponseEntity<RebuildAccepted> rebuild() {
        leaseGuard.requireOwner();
        coordinator.requestRebuild(LineageRebuildCoordinator.Trigger.MANUAL);
        return ResponseEntity.accepted().body(new RebuildAccepted(coordinator.currentGeneration()));
    }

    private LineageGraphSnapshot requireSnapshot() {
        return snapshotHolder.getForQuery()
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

    private LineageQueryResponse response(LineageGraphSnapshot snapshot, Object data) {
        return queryContext(snapshot).response(data);
    }

    private QueryContext queryContext(LineageGraphSnapshot snapshot) {
        long observedDbGeneration = generationReader.readCurrentGeneration();
        long ageSeconds = Math.max(
                0,
                Duration.between(snapshot.meta().builtAt(), Instant.now()).getSeconds());
        Optional<Throwable> rebuildError = coordinator.lastRebuildError();
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
        return new QueryContext(freshness);
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

    private record QueryContext(SnapshotFreshness snapshot) {

        private QueryContext {
            Objects.requireNonNull(snapshot, "snapshot");
        }

        private LineageQueryResponse response(Object data) {
            return new LineageQueryResponse(
                data,
                snapshot,
                new SourceFreshness(null, "UNKNOWN"));
        }
    }
}
