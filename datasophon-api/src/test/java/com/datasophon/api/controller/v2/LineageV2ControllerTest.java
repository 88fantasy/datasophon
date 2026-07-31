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
import com.datasophon.api.lineage.LineageJobDetailReader;
import com.datasophon.api.lineage.LineageLeaseGuard;
import com.datasophon.api.lineage.LineageRebuildCoordinator;
import com.datasophon.api.lineage.NodeMeta;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.springframework.jdbc.core.RowMapper;
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

    private static final String INGEST_TOKEN = "test-lineage-token";
    /** 查询端点分片后一律需要 clusterId（L3/D3）。 */
    private static final long CLUSTER_ID = 1L;
    /** stub 作业台账里存在的 jobId；其余一律视为不存在。 */
    private static final Set<Long> KNOWN_JOB_IDS = Set.of(1L, 2L);
    private static final AtomicReference<Instant> LAST_EVENT_RECEIVED_AT = new AtomicReference<>();

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
        LAST_EVENT_RECEIVED_AT.set(Instant.now().minusSeconds(30));
        // 分片后 published 是 Map、错误也按集群存：不能再整体 setField 覆盖引用
        // （两个字段都已是 final），改为取出容器后就地替换内容。
        publishedSnapshots().clear();
        publishedSnapshots().put(CLUSTER_ID, SNAPSHOT.get());
        rebuildErrors().clear();
        BLOCK_REBUILD.set(false);
        REBUILD_STARTED.set(new CountDownLatch(1));
        REBUILD_RELEASE.set(new CountDownLatch(1));
    }

    @SuppressWarnings("unchecked")
    private Map<Long, LineageGraphSnapshot> publishedSnapshots() {
        return (Map<Long, LineageGraphSnapshot>) ReflectionTestUtils.getField(snapshotHolder, "published");
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Throwable> rebuildErrors() {
        return (Map<Long, Throwable>) ReflectionTestUtils.getField(coordinator, "lastRebuildErrors");
    }

    @AfterEach
    void releaseBlockedRebuild() {
        BLOCK_REBUILD.set(false);
        REBUILD_RELEASE.get().countDown();
    }

    @Test
    void ingestRequiresClusterIdAndReturnsAdviceWrappedPojo() throws Exception {
        mockMvc.perform(post("/v2/lineage")
                .header("Authorization", "Bearer " + INGEST_TOKEN)
                .queryParam("clusterId", "7")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CHANGED"))
                .andExpect(jsonPath("$.data.jobId").value(11))
                .andExpect(jsonPath("$.data.definitionVersion").value(3));

        mockMvc.perform(post("/v2/lineage")
                .header("Authorization", "Bearer " + INGEST_TOKEN)
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
                .header("Authorization", "Bearer " + INGEST_TOKEN)
                .queryParam("clusterId", "7")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ingestRejectsMissingOrWrongBearerTokenBeforeTouchingIngestService() throws Exception {
        AtomicBoolean ingestInvoked = new AtomicBoolean(false);
        INGEST_HANDLER.set((clusterId, payload) -> {
            ingestInvoked.set(true);
            return new IngestResult(Status.CHANGED, 11L, 3, 2, 3);
        });

        mockMvc.perform(post("/v2/lineage")
                .queryParam("clusterId", "7")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/v2/lineage")
                .header("Authorization", "Bearer wrong-token")
                .queryParam("clusterId", "7")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized());

        assertThat(ingestInvoked).isFalse();
    }

    @Test
    void graphReturnsBoundedDataAndGenerationBasedStaleness() throws Exception {
        OBSERVED_GENERATION.set(8);

        mockMvc.perform(get("/v2/lineage/graph").queryParam("clusterId", "1")
                .queryParam("rootNodeId", "1")
                .queryParam("depth", "2")
                .queryParam("direction", "downstream"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data.nodes.length()").value(3))
                .andExpect(jsonPath("$.data.data.edges[0].jobs.length()").value(2))
                .andExpect(jsonPath("$.data.snapshot.generation").value(7))
                .andExpect(jsonPath("$.data.snapshot.targetGeneration").value(8))
                .andExpect(jsonPath("$.data.snapshot.stale").value(true))
                // resetMockService() 把 LAST_EVENT_RECEIVED_AT 设成 30s 前，远低于默认 1800s
                // 滞后阈值：sourceFreshness 与 snapshot 新鲜度是两件独立的事，快照 stale 不代表
                // 采集侧也 stale。
                .andExpect(jsonPath("$.data.sourceFreshness.status").value("OK"));

        mockMvc.perform(get("/v2/lineage/impact").queryParam("clusterId", "1")
                .queryParam("rootNodeId", "1")
                .queryParam("depth", "2"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void coordinatorErrorParticipatesInFreshnessAndImpactFailsClosed() throws Exception {
        rebuildErrors().put(CLUSTER_ID, new IllegalStateException("load failed"));

        mockMvc.perform(get("/v2/lineage/graph").queryParam("clusterId", "1")
                .queryParam("rootNodeId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.snapshot.stale").value(true))
                .andExpect(jsonPath("$.data.snapshot.lastRebuildError").value("load failed"));

        mockMvc.perform(get("/v2/lineage/impact").queryParam("clusterId", "1")
                .queryParam("rootNodeId", "1")
                .queryParam("depth", "2"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void snapshotAgeIsTheThirdIndependentStalenessBranch() throws Exception {
        SNAPSHOT.set(snapshot(7, Instant.now().minusSeconds(601)));
        publishedSnapshots().put(CLUSTER_ID, SNAPSHOT.get());

        mockMvc.perform(get("/v2/lineage/graph").queryParam("clusterId", "1")
                .queryParam("rootNodeId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.snapshot.generation").value(7))
                .andExpect(jsonPath("$.data.snapshot.targetGeneration").value(7))
                .andExpect(jsonPath("$.data.snapshot.stale").value(true));
    }

    @Test
    void impactIsDownstreamAndReturnsGraphShapeWhenFresh() throws Exception {
        mockMvc.perform(get("/v2/lineage/impact").queryParam("clusterId", "1")
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
        mockMvc.perform(get("/v2/lineage/graph").queryParam("clusterId", "1")
                .queryParam("rootNodeId", "1")
                .queryParam("direction", "upstream")
                .queryParam("expand", "n:2:down:g7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data.nodes[0].id").value(2))
                .andExpect(jsonPath("$.data.data.nodes[1].id").value(3));

        mockMvc.perform(get("/v2/lineage/graph").queryParam("clusterId", "1")
                .queryParam("rootNodeId", "1")
                .queryParam("expand", "n:2:down:g6"))
                .andExpect(status().isConflict());
    }

    @Test
    void graphRejectsMissingRootInvalidDepthAndInvalidDirection() throws Exception {
        mockMvc.perform(get("/v2/lineage/graph").queryParam("clusterId", "1")
                .queryParam("rootNodeId", "999"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/v2/lineage/graph").queryParam("clusterId", "1")
                .queryParam("rootNodeId", "1")
                .queryParam("depth", "0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/v2/lineage/graph").queryParam("clusterId", "1")
                .queryParam("rootNodeId", "1")
                .queryParam("depth", "6"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/v2/lineage/graph").queryParam("clusterId", "1")
                .queryParam("rootNodeId", "1")
                .queryParam("direction", "sideways"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/v2/lineage/graph").queryParam("clusterId", "1")
                .queryParam("rootNodeId", "1")
                .queryParam("direction", "UPSTREAM"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overviewIncludesUnknownAndAllActualLayerEdgePairs() throws Exception {
        mockMvc.perform(get("/v2/lineage/overview").queryParam("clusterId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data.edges.length()").value(2))
                .andExpect(jsonPath("$.data.data.edges[0].srcLayer").value("ODS"))
                .andExpect(jsonPath("$.data.data.edges[0].dstLayer").value("DWD"))
                .andExpect(jsonPath("$.data.data.edges[1].srcLayer").value("DWD"))
                .andExpect(jsonPath("$.data.data.edges[1].dstLayer").value("UNKNOWN"));
    }

    @Test
    void tableReturnsSnapshotNodeAndMissingNodeIsNotFound() throws Exception {
        mockMvc.perform(get("/v2/lineage/table/2").queryParam("clusterId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data.id").value(2))
                .andExpect(jsonPath("$.data.data.dwLayer").value("DWD"));

        mockMvc.perform(get("/v2/lineage/table/999").queryParam("clusterId", "1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingSnapshotReturnsServiceUnavailableInsteadOfEmptyGraph() throws Exception {
        publishedSnapshots().remove(CLUSTER_ID);

        mockMvc.perform(get("/v2/lineage/graph").queryParam("clusterId", "1")
                .queryParam("rootNodeId", "1"))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/v2/lineage/overview").queryParam("clusterId", "1"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void rebuildReturnsAcceptedBeforeLoaderCompletes() throws Exception {
        BLOCK_REBUILD.set(true);

        mockMvc.perform(post("/v2/lineage/rebuild").queryParam("clusterId", "1"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.generation").value(7));

        assertThat(REBUILD_STARTED.get().await(5, TimeUnit.SECONDS)).isTrue();
        REBUILD_RELEASE.get().countDown();
    }

    @Test
    void nonOwnerRejectsLineageEndpointsAndReportsReadinessDown() throws Exception {
        LEASE_OWNER.set(false);

        mockMvc.perform(post("/v2/lineage")
                .header("Authorization", "Bearer " + INGEST_TOKEN)
                .queryParam("clusterId", "7")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/v2/lineage/graph").queryParam("clusterId", "1")
                .queryParam("rootNodeId", "1"))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(post("/v2/lineage/rebuild").queryParam("clusterId", "1"))
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
                new NodeMeta(1, 1L, "paimon", "prod", "ods", "orders", "paimon://prod/ods/orders", "ODS");
        NodeMeta middle =
                new NodeMeta(2, 1L, "paimon", "prod", "dwd", "orders", "paimon://prod/dwd/orders", "DWD");
        NodeMeta target =
                new NodeMeta(3, 1L, "paimon", "prod", "misc", "orders", "paimon://prod/misc/orders", null);
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
                                                LineageGraphQuery graphQuery,
                                                LineageJobDetailReader jobDetailReader) {
            return new LineageV2Controller(
                    ingestService,
                    leaseGuard,
                    snapshotHolder,
                    coordinator,
                    generationReader,
                    graphQuery,
                    jobDetailReader,
                    600,
                    1800,
                    INGEST_TOKEN);
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

        /**
         * 作业详情读取器：切片测试没有真实 DataSource，故注入一个按 SQL 分流的 stub JdbcTemplate。
         *
         * <p>{@code LineageJobDetailReader} 是 final 类，不为测试放开继承 —— 从依赖侧打桩即可。
         * 作业查询只认 {@link #CLUSTER_ID}，用于验证跨集群读取被挡成 404。</p>
         */
        @Bean
        LineageJobDetailReader lineageJobDetailReader() {
            JdbcTemplate jdbcTemplate = new JdbcTemplate() {

                @SuppressWarnings("unchecked")
                @Override
                public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
                    if (sql.contains("MAX(e.received_at)")) {
                        return (List<T>) singletonOrEmpty(LAST_EVENT_RECEIVED_AT.get());
                    }
                    long clusterId = ((Number) args[0]).longValue();
                    long jobId = ((Number) args[1]).longValue();
                    if (clusterId != CLUSTER_ID || !KNOWN_JOB_IDS.contains(jobId)) {
                        return List.of();
                    }
                    return (List<T>) List.of(new LineageJobDetailReader.JobDetail(
                            jobId, clusterId, "job_" + jobId, "SPARK", "SPARK_SQL", "DWD", "owner",
                            "http://scheduler/job/" + jobId, "RUNNING", Instant.parse("2026-07-31T00:00:00Z")));
                }
            };
            return new LineageJobDetailReader(jdbcTemplate);
        }

        private static List<Timestamp> singletonOrEmpty(Instant instant) {
            return instant == null ? List.of() : List.of(Timestamp.from(instant));
        }

        @Bean
        LineageGenerationReader lineageGenerationReader() {
            // 分片后 readCurrentGeneration 走 queryForList(sql, type, clusterId)，
            // 不再是 queryForObject —— stub 必须跟着换，否则会打到没有 DataSource 的真实实现。
            JdbcTemplate jdbcTemplate = new JdbcTemplate() {
                @Override
                public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
                    return List.of(elementType.cast(OBSERVED_GENERATION.get()));
                }
            };
            return new LineageGenerationReader(jdbcTemplate);
        }

        @Bean
        LineageRebuildCoordinator lineageRebuildCoordinator(LineageGraphSnapshotHolder holder) {
            LineageRebuildCoordinator.SnapshotLoader loader = new LineageRebuildCoordinator.SnapshotLoader() {

                @Override
                public LineageGraphSnapshot load(long clusterId) throws Exception {
                    if (BLOCK_REBUILD.get()) {
                        REBUILD_STARTED.get().countDown();
                        if (!REBUILD_RELEASE.get().await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("timed out waiting to release rebuild");
                        }
                    }
                    return SNAPSHOT.get();
                }

                @Override
                public Collection<Long> knownClusterIds() {
                    return List.of(CLUSTER_ID);
                }
            };
            return new LineageRebuildCoordinator(holder, loader, new TransactionTemplate(transactionManager()));
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
