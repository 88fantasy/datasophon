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

import static org.assertj.core.api.Assertions.assertThat;

import com.datasophon.api.lineage.event.OpenLineageEventDecoder;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class LineageIngestComponentsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void openLineageDecoderKeepsProviderTypesBehindNeutralBoundary() throws Exception {
        DecodedLineageEvent event = new OpenLineageEventDecoder().decode(objectMapper.readTree("""
                {
                  "producer": "https://example.test/spark",
                  "engine": "spark",
                  "eventType": "COMPLETE",
                  "eventTime": "2026-07-30T00:00:00Z",
                  "run": {
                    "runId": "run-1",
                    "facets": {"nominalTime": {"nominalStartTime": "2026-07-29T23:59:00Z"}}
                  },
                  "job": {
                    "name": "daily-orders",
                    "facets": {
                      "jobType": {"processingType": "BATCH", "integration": "SPARK"},
                      "sql": {"query": "insert into dwd.orders select * from ods.orders"}
                    }
                  },
                  "inputs": [{"namespace": "paimon://prod/ods", "name": "orders"}],
                  "outputs": [{"namespace": "paimon://prod/dwd", "name": "orders"}]
                }
                """));

        assertThat(event.engine()).isEqualTo("spark");
        assertThat(event.flowType()).isEqualTo("BATCH");
        assertThat(event.inputs()).containsExactly(new DatasetIdentity("paimon://prod/ods", "orders"));
        assertThat(event.outputs()).containsExactly(new DatasetIdentity("paimon://prod/dwd", "orders"));
    }

    @Test
    void defaultCanonicalResolverHandlesCatalogStyleNamespace() {
        CanonicalNameResolver resolver = new CanonicalNameResolver.Default();

        assertThat(resolver.resolve(new DatasetIdentity("paimon://prod/dwd", "orders")))
                .get()
                .extracting(ResolvedDataset::canonicalName)
                .isEqualTo("paimon://prod/dwd/orders");
        assertThat(resolver.resolve(new DatasetIdentity("prod.dwd", "orders"))).isEmpty();
    }

    /**
     * 2026-07-30 用 deploy/deployment-standalone-doris.md 沙箱对真实
     * openlineage-spark 1.29.0 实机采样确认的格式（见 docs/monitoring/data-lineage-verification.md
     * §3.5）：namespace 只到 scheme://host:port，name 是 database.table。
     */
    @Test
    void defaultCanonicalResolverHandlesJdbcStyleNamespaceSampledFromRealSpark() {
        CanonicalNameResolver resolver = new CanonicalNameResolver.Default();

        ResolvedDataset resolved = resolver
                .resolve(new DatasetIdentity("mysql://192.168.10.131:3306", "datasophon.t_ddh_frame_service"))
                .orElseThrow();

        assertThat(resolved.connector()).isEqualTo("mysql");
        assertThat(resolved.catalogName()).isEqualTo("192.168.10.131:3306");
        assertThat(resolved.databaseName()).isEqualTo("datasophon");
        assertThat(resolved.tableName()).isEqualTo("t_ddh_frame_service");
        assertThat(resolved.canonicalName()).isEqualTo("mysql://192.168.10.131:3306/datasophon/t_ddh_frame_service");
    }

    /**
     * Doris 经标准 JDBC（9030）访问时，openlineage-spark 产出的 scheme 是 {@code mysql} 而不是
     * {@code doris}——JDBC facet 由驱动类/连接串决定，不识别后端产品身份。已实机采样确认，见同上。
     */
    @Test
    void defaultCanonicalResolverResolvesDorisOverJdbcUnderMysqlConnector() {
        CanonicalNameResolver resolver = new CanonicalNameResolver.Default();

        ResolvedDataset resolved = resolver
                .resolve(new DatasetIdentity("mysql://192.168.10.131:9030", "l0_probe.doris_output"))
                .orElseThrow();

        assertThat(resolved.connector()).isEqualTo("mysql");
        assertThat(resolved.canonicalName()).isEqualTo("mysql://192.168.10.131:9030/l0_probe/doris_output");
    }

    @Test
    void defaultCanonicalResolverRejectsAmbiguousJdbcStyleNames() {
        CanonicalNameResolver resolver = new CanonicalNameResolver.Default();

        assertThat(resolver.resolve(new DatasetIdentity("mysql://host:3306", "orders"))).isEmpty();
        assertThat(resolver.resolve(new DatasetIdentity("mysql://host:3306", "catalog.schema.table"))).isEmpty();
    }

    @Test
    void structuralHashSortsAndDeduplicatesEachSide() {
        StructuralHashCalculator calculator = new StructuralHashCalculator(objectMapper);
        ResolvedDataset a = dataset("paimon://prod/ods/a");
        ResolvedDataset b = dataset("paimon://prod/ods/b");
        ResolvedDataset output = dataset("paimon://prod/dwd/orders");

        String first = calculator.structuralHash(List.of(b, a, a), List.of(output));
        String second = calculator.structuralHash(List.of(a, b), List.of(output, output));

        assertThat(first).isEqualTo(second).hasSize(64);
    }

    @Test
    void watermarkUsesNominalThenEventTimeThenReceivedAt() {
        WatermarkExtractor extractor = new WatermarkExtractor.Default();
        Instant receivedAt = Instant.parse("2026-07-30T03:00:00Z");

        assertThat(extractor.extract(event("2026-07-30T01:00:00Z", "2026-07-30T02:00:00Z"), receivedAt))
                .isEqualTo(new WatermarkExtractor.Extraction(
                        Instant.parse("2026-07-30T01:00:00Z").toEpochMilli(),
                        WatermarkExtractor.Source.NOMINAL_TIME));
        assertThat(extractor.extract(event(null, "2026-07-30T02:00:00Z"), receivedAt).source())
                .isEqualTo(WatermarkExtractor.Source.EVENT_TIME);
        assertThat(extractor.extract(event(null, null), receivedAt))
                .satisfies(extraction -> {
                    assertThat(extraction.epochMillis()).isEqualTo(receivedAt.toEpochMilli());
                    assertThat(extraction.degraded()).isTrue();
                });
    }

    @Test
    void ingestServiceHasNoSnapshotDependency() {
        assertThat(List.of(LineageIngestService.class.getDeclaredFields()))
                .noneMatch(field -> field.getType() == LineageGraphSnapshotHolder.class
                        || field.getType() == LineageGraphSnapshot.class);
    }

    private static ResolvedDataset dataset(String canonicalName) {
        String[] segments = canonicalName.substring("paimon://".length()).split("/");
        return new ResolvedDataset("paimon", segments[0], segments[1], segments[2], canonicalName, null);
    }

    private static DecodedLineageEvent event(String nominalTime, String eventTime) {
        return new DecodedLineageEvent("producer", "run", "COMPLETE", "job", "spark", "BATCH", nominalTime,
                eventTime, null, List.of(), List.of());
    }
}
