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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datasophon.api.lineage.EdgeValue;
import com.datasophon.api.lineage.JobRef;
import com.datasophon.api.lineage.LineageGenerationReader;
import com.datasophon.api.lineage.LineageGraphQuery;
import com.datasophon.api.lineage.LineageGraphSnapshot;
import com.datasophon.api.lineage.LineageGraphSnapshotHolder;
import com.datasophon.api.lineage.LineageIngestOperations;
import com.datasophon.api.lineage.LineageIngestService.IngestResult;
import com.datasophon.api.lineage.LineageIngestService.Status;
import com.datasophon.api.lineage.LineageLeaseGuard;
import com.datasophon.api.lineage.LineageRebuildCoordinator;
import com.datasophon.api.lineage.NodeMeta;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.context.web.ServletTestExecutionListener;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;

@WebMvcTest(useDefaultFilters = false)
@Import({LineageV2ControllerTest.WebConfiguration.class, V2ResponseBodyAdvice.class, V2ApiExceptionHandler.class})
@TestExecutionListeners(listeners = {
        ServletTestExecutionListener.class,
        DependencyInjectionTestExecutionListener.class,
        DirtiesContextTestExecutionListener.class
}, mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
class LineageV2ControllerTest {

    private static final AtomicReference<BiFunction<Long, JsonNode, IngestResult>> INGEST_HANDLER =
            new AtomicReference<>();
    private static final AtomicBoolean LEASE_OWNER = new AtomicBoolean();
    private static final AtomicLong OBSERVED_GENERATION = new AtomicLong();
    private static final AtomicReference<LineageGraphSnapshot> SNAPSHOT = new AtomicReference<>();
    private static final AtomicBoolean BLOCK_REBUILD = new AtomicBoolean();
    private static final AtomicReference<CountDownLatch> REBUILD_STARTED =
            new AtomicReference<>(new CountDownLatch(1));
    private static final AtomicReference<CountDownLatch> REBUILD_RELEASE =
            new AtomicReference<>(new CountDownLatch(1));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LineageGraphSnapshotHolder snapshotHolder;

    @Autowired
    private LineageRebuildCoordinator coordinator;

    @BeforeEach
    void resetMockService() {
        LEASE_OWNER.set(true);
        OBSERVED_GENERATION.set(7);
        INGEST_HANDLER.set((clusterId, payload) -> new IngestResult(Status.CHANGED, 11L, 3, 2, 3));
        SNAPSHOT.set(snapshot(7, Instant.now().minusSeconds(10)));
        ReflectionTestUtils.setField(snapshotHolder, "published", SNAPSHOT.get());
        ReflectionTestUtils.setField(coordinator, "lastRebuildError", null);
        BLOCK_REBUILD.set(false);
        REBUILD_STARTED.set(new CountDownLatch(1));
        REBUILD_RELEASE.set(new CountDownLatch(1));
    }

    @AfterEach
    void releaseBlockedRebuild() {
        BLOCK_REBUILD.set(false);
        REBUILD_RELEASE.get().countDown();
    }

    @Test
    void ingestRequiresClusterIdAndReturnsAdviceWrappedPojo() throws Exception {
        mockMvc.perform(post("/v2/lineage")
                .queryParam("clusterId", "7")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CHANGED"))
                .andExpect(jsonPath("$.data.jobId").value(11))
                .andExpect(jsonPath("$.data.definitionVersion").value(3));

        mockMvc.perform(post("/v2/lineage")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ingestMapsInvalidPayloadToBadRequest() throws Exception {
        INGEST_HANDLER.set((clusterId, payload) -> {
            throw new IllegalArgumentException("producer must not be blank");
        });

        mockMvc.perform(post("/v2/lineage")
                .queryParam("clusterId", "7")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void graphReturnsBoundedDataAndGenerationBasedStaleness() throws Exception {
        OBSERVED_GENERATION.set(8);

        mockMvc.perform(get("/v2/lineage/graph")
                .queryParam("rootNodeId", "1")
                .queryParam("depth", "2")
                .queryParam("direction", "downstream"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data.nodes.length()").value(3))
                .andExpect(jsonPath("$.data.data.edges[0].jobs.length()").value(2))
                .andExpect(jsonPath("$.data.snapshot.generation").value(7))
                .andExpect(jsonPath("$.data.snapshot.targetGeneration").value(8))
                .andExpect(jsonPath("$.data.snapshot.stale").value(true))
                .andExpect(jsonPath("$.data.sourceFreshness.status").value("UNKNOWN"));

        mockMvc.perform(get("/v2/lineage/impact")
                .queryParam("rootNodeId", "1")
                .queryParam("depth", "2"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void coordinatorErrorParticipatesInFreshnessAndImpactFailsClosed() throws Exception {
        ReflectionTestUtils.setField(coordinator, "lastRebuildError", new IllegalStateException("load failed"));

        mockMvc.perform(get("/v2/lineage/graph")
                .queryParam("rootNodeId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.snapshot.stale").value(true))
                .andExpect(jsonPath("$.data.snapshot.lastRebuildError").value("load failed"));

        mockMvc.perform(get("/v2/lineage/impact")
                .queryParam("rootNodeId", "1")
                .queryParam("depth", "2"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void snapshotAgeIsTheThirdIndependentStalenessBranch() throws Exception {
        SNAPSHOT.set(snapshot(7, Instant.now().minusSeconds(601)));
        ReflectionTestUtils.setField(snapshotHolder, "published", SNAPSHOT.get());

        mockMvc.perform(get("/v2/lineage/graph")
                .queryParam("rootNodeId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.snapshot.generation").value(7))
                .andExpect(jsonPath("$.data.snapshot.targetGeneration").value(7))
                .andExpect(jsonPath("$.data.snapshot.stale").value(true));
    }

    @Test
    void impactIsDownstreamAndReturnsGraphShapeWhenFresh() throws Exception {
        mockMvc.perform(get("/v2/lineage/impact")
                .queryParam("rootNodeId", "2")
                .queryParam("depth", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data.nodes[0].id").value(2))
                .andExpect(jsonPath("$.data.data.nodes[1].id").value(3))
                .andExpect(jsonPath("$.data.data.edges[0].src").value(2))
                .andExpect(jsonPath("$.data.data.edges[0].dst").value(3));
    }

    @Test
    void graphExpansionUsesTokenNodeAndRejectsGenerationMismatch() throws Exception {
        mockMvc.perform(get("/v2/lineage/graph")
                .queryParam("rootNodeId", "1")
                .queryParam("direction", "upstream")
                .queryParam("expand", "n:2:down:g7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data.nodes[0].id").value(2))
                .andExpect(jsonPath("$.data.data.nodes[1].id").value(3));

        mockMvc.perform(get("/v2/lineage/graph")
                .queryParam("rootNodeId", "1")
                .queryParam("expand", "n:2:down:g6"))
                .andExpect(status().isConflict());
    }

    @Test
    void graphRejectsMissingRootInvalidDepthAndInvalidDirection() throws Exception {
        mockMvc.perform(get("/v2/lineage/graph")
                .queryParam("rootNodeId", "999"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/v2/lineage/graph")
                .queryParam("rootNodeId", "1")
                .queryParam("depth", "0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/v2/lineage/graph")
                .queryParam("rootNodeId", "1")
                .queryParam("depth", "6"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/v2/lineage/graph")
                .queryParam("rootNodeId", "1")
                .queryParam("direction", "sideways"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/v2/lineage/graph")
                .queryParam("rootNodeId", "1")
                .queryParam("direction", "UPSTREAM"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overviewIncludesUnknownAndAllActualLayerEdgePairs() throws Exception {
        mockMvc.perform(get("/v2/lineage/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data.edges.length()").value(2))
                .andExpect(jsonPath("$.data.data.edges[0].srcLayer").value("ODS"))
                .andExpect(jsonPath("$.data.data.edges[0].dstLayer").value("DWD"))
                .andExpect(jsonPath("$.data.data.edges[1].srcLayer").value("DWD"))
                .andExpect(jsonPath("$.data.data.edges[1].dstLayer").value("UNKNOWN"));
    }

    @Test
    void tableReturnsSnapshotNodeAndMissingNodeIsNotFound() throws Exception {
        mockMvc.perform(get("/v2/lineage/table/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data.id").value(2))
                .andExpect(jsonPath("$.data.data.dwLayer").value("DWD"));

        mockMvc.perform(get("/v2/lineage/table/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingSnapshotReturnsServiceUnavailableInsteadOfEmptyGraph() throws Exception {
        ReflectionTestUtils.setField(snapshotHolder, "published", null);

        mockMvc.perform(get("/v2/lineage/graph")
                .queryParam("rootNodeId", "1"))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/v2/lineage/overview"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void rebuildReturnsAcceptedBeforeLoaderCompletes() throws Exception {
        BLOCK_REBUILD.set(true);

        mockMvc.perform(post("/v2/lineage/rebuild"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.generation").value(7));

        assertThat(REBUILD_STARTED.get().await(5, TimeUnit.SECONDS)).isTrue();
        REBUILD_RELEASE.get().countDown();
    }

    @Test
    void nonOwnerRejectsLineageEndpointsAndReportsReadinessDown() throws Exception {
        LEASE_OWNER.set(false);

        mockMvc.perform(post("/v2/lineage")
                .queryParam("clusterId", "7")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/v2/lineage/graph")
                .queryParam("rootNodeId", "1"))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(post("/v2/lineage/rebuild"))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/v2/lineage/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.data.owner").value(false))
                .andExpect(jsonPath("$.data.status").value("DOWN"))
                .andExpect(jsonPath("$.data.message").value(LineageLeaseGuard.UNAVAILABLE_MESSAGE));
    }

    @Test
    void ownerReadinessIsUp() throws Exception {
        mockMvc.perform(get("/v2/lineage/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.owner").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    private static LineageGraphSnapshot snapshot(long generation, Instant builtAt) {
        NodeMeta source =
                new NodeMeta(1, "paimon", "prod", "ods", "orders", "paimon://prod/ods/orders", "ODS");
        NodeMeta middle =
                new NodeMeta(2, "paimon", "prod", "dwd", "orders", "paimon://prod/dwd/orders", "DWD");
        NodeMeta target =
                new NodeMeta(3, "paimon", "prod", "misc", "orders", "paimon://prod/misc/orders", null);
        MutableValueGraph<Long, EdgeValue> graph = ValueGraphBuilder.<Long, EdgeValue>directed()
                .allowsSelfLoops(true)
                .build();
        graph.putEdgeValue(
                1L,
                2L,
                new EdgeValue(List.of(
                        new JobRef(1, 101, 1, "BATCH"),
                        new JobRef(2, 102, 1, "STREAM"))));
        graph.putEdgeValue(2L, 3L, new EdgeValue(List.of(new JobRef(3, 103, 1, "BATCH"))));
        return LineageGraphSnapshot.copyOf(graph, Map.of(1L, source, 2L, middle, 3L, target), generation, builtAt);
    }

    @Configuration(proxyBeanMethods = false)
    static class WebConfiguration {

        @Bean
        LineageV2Controller lineageV2Controller(
                                                LineageIngestOperations ingestService,
                                                LineageLeaseGuard leaseGuard,
                                                LineageGraphSnapshotHolder snapshotHolder,
                                                LineageRebuildCoordinator coordinator,
                                                LineageGenerationReader generationReader,
                                                LineageGraphQuery graphQuery) {
            return new LineageV2Controller(
                    ingestService,
                    leaseGuard,
                    snapshotHolder,
                    coordinator,
                    generationReader,
                    graphQuery,
                    600);
        }

        @Bean
        LineageIngestOperations ingestService() {
            return (clusterId, payload) -> INGEST_HANDLER.get().apply(clusterId, payload);
        }

        @Bean
        LineageLeaseGuard lineageLeaseGuard() {
            return new LineageLeaseGuard(LEASE_OWNER::get);
        }

        @Bean
        LineageGraphSnapshotHolder lineageGraphSnapshotHolder() {
            return new LineageGraphSnapshotHolder();
        }

        @Bean
        LineageGraphQuery lineageGraphQuery() {
            return new LineageGraphQuery();
        }

        @Bean
        LineageGenerationReader lineageGenerationReader() {
            JdbcTemplate jdbcTemplate = new JdbcTemplate() {
                @Override
                public <T> T queryForObject(String sql, Class<T> requiredType) {
                    return requiredType.cast(OBSERVED_GENERATION.get());
                }
            };
            return new LineageGenerationReader(jdbcTemplate);
        }

        @Bean
        LineageRebuildCoordinator lineageRebuildCoordinator(LineageGraphSnapshotHolder holder) {
            return new LineageRebuildCoordinator(holder, () -> {
                if (BLOCK_REBUILD.get()) {
                    REBUILD_STARTED.get().countDown();
                    if (!REBUILD_RELEASE.get().await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("timed out waiting to release rebuild");
                    }
                }
                return SNAPSHOT.get();
            }, new TransactionTemplate(transactionManager()));
        }

        private static PlatformTransactionManager transactionManager() {
            return new PlatformTransactionManager() {
                @Override
                public TransactionStatus getTransaction(TransactionDefinition definition) {
                    return new SimpleTransactionStatus();
                }

                @Override
                public void commit(TransactionStatus status) {
                }

                @Override
                public void rollback(TransactionStatus status) {
                }
            };
        }
    }
}
