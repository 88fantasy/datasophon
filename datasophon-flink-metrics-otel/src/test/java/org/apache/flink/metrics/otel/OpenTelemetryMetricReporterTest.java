/*
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.flink.metrics.otel;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.flink.metrics.CharacterFilter;
import org.apache.flink.metrics.Gauge;
import org.apache.flink.metrics.Histogram;
import org.apache.flink.metrics.HistogramStatistics;
import org.apache.flink.metrics.LogicalScopeProvider;
import org.apache.flink.metrics.MetricGroup;
import org.apache.flink.metrics.Meter;
import org.apache.flink.metrics.MetricConfig;
import org.apache.flink.metrics.SimpleCounter;
import org.apache.flink.metrics.groups.UnregisteredMetricsGroup;
import org.junit.jupiter.api.Test;

class OpenTelemetryMetricReporterTest {

    @Test
    void exportsCounterAsDottedDeltaSumWithFlinkAttributes() {
        OpenTelemetryMetricReporter reporter = new OpenTelemetryMetricReporter();
        SimpleCounter counter = new SimpleCounter();
        reporter.notifyOfAddedMetric(counter, "numRecordsOut", new TestMetricGroup());

        counter.inc(50);
        MetricData first = onlyMetric(reporter.collectAllMetrics());
        assertEquals("flink.taskmanager.job.task.operator.numRecordsOut", first.getName());
        assertEquals(AggregationTemporality.DELTA, first.getLongSumData().getAggregationTemporality());
        assertTrue(first.getLongSumData().isMonotonic());
        assertEquals(50L, first.getLongSumData().getPoints().iterator().next().getValue());
        assertEquals(
                "job-123",
                first.getLongSumData()
                        .getPoints()
                        .iterator()
                        .next()
                        .getAttributes()
                        .get(AttributeKey.stringKey("job_id")));

        counter.inc(7);
        MetricData second = onlyMetric(reporter.collectAllMetrics());
        assertEquals(7L, second.getLongSumData().getPoints().iterator().next().getValue());
    }

    @Test
    void exportsNumericGaugeWithoutChangingItsValue() {
        OpenTelemetryMetricReporter reporter = new OpenTelemetryMetricReporter();
        Gauge<Long> gauge = () -> 1728L;
        reporter.notifyOfAddedMetric(gauge, "Heap.Used", new TestMetricGroup());

        MetricData metric = onlyMetric(reporter.collectAllMetrics());

        assertEquals("flink.taskmanager.job.task.operator.Heap.Used", metric.getName());
        assertEquals(MetricDataType.LONG_GAUGE, metric.getType());
        assertEquals(1728L, metric.getLongGaugeData().getPoints().iterator().next().getValue());
    }

    @Test
    void skipsNonNumericGauge() {
        OpenTelemetryMetricReporter reporter = new OpenTelemetryMetricReporter();
        reporter.notifyOfAddedMetric((Gauge<String>) () -> "ready", "status", new TestMetricGroup());

        assertTrue(reporter.collectAllMetrics().isEmpty());
    }

    @Test
    void exportsMeterAsDeltaCountAndCurrentRate() {
        OpenTelemetryMetricReporter reporter = new OpenTelemetryMetricReporter();
        AtomicLong count = new AtomicLong(40L);
        AtomicReference<Double> rate = new AtomicReference<>(2.5D);
        Meter meter =
                new Meter() {
                    @Override
                    public void markEvent() {
                        count.incrementAndGet();
                    }

                    @Override
                    public void markEvent(long n) {
                        count.addAndGet(n);
                    }

                    @Override
                    public double getRate() {
                        return rate.get();
                    }

                    @Override
                    public long getCount() {
                        return count.get();
                    }
                };
        reporter.notifyOfAddedMetric(meter, "recordsOutPerSecond", new TestMetricGroup());

        Map<String, MetricData> first = metricsByName(reporter.collectAllMetrics());
        assertEquals(40L, longPoint(first.get("flink.taskmanager.job.task.operator.recordsOutPerSecond.count")));
        assertEquals(
                2.5D,
                first.get("flink.taskmanager.job.task.operator.recordsOutPerSecond.rate")
                        .getDoubleGaugeData()
                        .getPoints()
                        .iterator()
                        .next()
                        .getValue());

        count.set(47L);
        assertEquals(
                7L,
                longPoint(
                        metricsByName(reporter.collectAllMetrics())
                                .get("flink.taskmanager.job.task.operator.recordsOutPerSecond.count")));
    }

    @Test
    void exportsHistogramAsSummary() {
        OpenTelemetryMetricReporter reporter = new OpenTelemetryMetricReporter();
        Histogram histogram =
                new Histogram() {
                    @Override
                    public void update(long value) {}

                    @Override
                    public long getCount() {
                        return 3L;
                    }

                    @Override
                    public HistogramStatistics getStatistics() {
                        return new FixedHistogramStatistics();
                    }
                };
        reporter.notifyOfAddedMetric(histogram, "checkpointAlignment", new TestMetricGroup());

        MetricData metric = onlyMetric(reporter.collectAllMetrics());

        assertEquals(MetricDataType.SUMMARY, metric.getType());
        assertEquals(3L, metric.getSummaryData().getPoints().iterator().next().getCount());
        assertEquals(30D, metric.getSummaryData().getPoints().iterator().next().getSum());
        assertEquals(6, metric.getSummaryData().getPoints().iterator().next().getValues().size());
    }

    @Test
    void appliesOtelResourceConfigurationAndClosesBeforeFirstReport() {
        OpenTelemetryMetricReporter reporter = new OpenTelemetryMetricReporter();
        MetricConfig config = new MetricConfig();
        config.setProperty("exporter.endpoint", "http://127.0.0.1:4317");
        config.setProperty("service.name", "flink-cdc");
        config.setProperty("service.version", "1.20.5");

        reporter.open(config);
        SimpleCounter counter = new SimpleCounter();
        reporter.notifyOfAddedMetric(counter, "records", new TestMetricGroup());
        MetricData metric = onlyMetric(reporter.collectAllMetrics());

        assertEquals("flink-cdc", metric.getResource().getAttribute(ResourceAttributes.SERVICE_NAME));
        assertEquals("1.20.5", metric.getResource().getAttribute(ResourceAttributes.SERVICE_VERSION));
        reporter.close();
    }

    @Test
    void requiresExporterEndpoint() {
        OpenTelemetryMetricReporter reporter = new OpenTelemetryMetricReporter();

        assertThrows(IllegalArgumentException.class, () -> reporter.open(new MetricConfig()));
    }

    @Test
    void reportSendsCollectedMetricsToConfiguredExporter() {
        OpenTelemetryMetricReporter reporter = new OpenTelemetryMetricReporter();
        RecordingMetricExporter exporter = new RecordingMetricExporter();
        reporter.exporter = exporter;
        SimpleCounter counter = new SimpleCounter();
        counter.inc(9L);
        reporter.notifyOfAddedMetric(counter, "records", new TestMetricGroup());

        reporter.report();

        assertEquals(1, exporter.exported.size());
        assertEquals(9L, longPoint(exporter.exported.get(0)));
        reporter.close();
        assertTrue(exporter.shutdown);
    }

    @Test
    void removesMetricBeforeFirstCollection() {
        OpenTelemetryMetricReporter reporter = new OpenTelemetryMetricReporter();
        SimpleCounter counter = new SimpleCounter();
        TestMetricGroup group = new TestMetricGroup();
        reporter.notifyOfAddedMetric(counter, "records", group);

        assertDoesNotThrow(() -> reporter.notifyOfRemovedMetric(counter, "records", group));
        assertTrue(reporter.collectAllMetrics().isEmpty());
    }

    @Test
    void initializesCollectionStartTimeOnOpenInsteadOfEpochZero() {
        Instant openedAt = Instant.parse("2026-08-24T10:15:30.123456789Z");
        OpenTelemetryMetricReporter reporter =
                new OpenTelemetryMetricReporter(Clock.fixed(openedAt, ZoneOffset.UTC));
        MetricConfig config = new MetricConfig();
        config.setProperty("exporter.endpoint", "http://127.0.0.1:4317");
        reporter.open(config);
        SimpleCounter counter = new SimpleCounter();
        counter.inc(5L);
        reporter.notifyOfAddedMetric(counter, "records", new TestMetricGroup());

        LongPointData point =
                onlyMetric(reporter.collectAllMetrics())
                        .getLongSumData()
                        .getPoints()
                        .iterator()
                        .next();

        long openedAtNanos =
                TimeUnit.SECONDS.toNanos(openedAt.getEpochSecond()) + openedAt.getNano();
        assertEquals(openedAtNanos, point.getStartEpochNanos());
        assertEquals(openedAtNanos, point.getEpochNanos());
        reporter.close();
    }

    @Test
    void stopsExportingAfterCloseAndClosesIdempotently() {
        OpenTelemetryMetricReporter reporter = new OpenTelemetryMetricReporter();
        RecordingMetricExporter exporter = new RecordingMetricExporter();
        reporter.exporter = exporter;
        SimpleCounter counter = new SimpleCounter();
        counter.inc(3L);
        reporter.notifyOfAddedMetric(counter, "records", new TestMetricGroup());

        reporter.close();
        assertTrue(exporter.shutdown);

        // Flink shuts the reporter scheduler down only after close(), so a queued report() must
        // neither throw nor export. Clearing the exporter reference instead would turn that
        // report() into a NullPointerException that report()'s own catch block hides, so assert
        // the reference survives rather than relying on the absence of a throw.
        assertSame(exporter, reporter.exporter);
        assertDoesNotThrow(reporter::report);
        assertTrue(exporter.exported.isEmpty());
        assertDoesNotThrow(reporter::close);
    }

    private static MetricData onlyMetric(Collection<MetricData> metrics) {
        assertEquals(1, metrics.size());
        return metrics.iterator().next();
    }

    private static Map<String, MetricData> metricsByName(Collection<MetricData> metrics) {
        return metrics.stream()
                .collect(
                        java.util.stream.Collectors.toMap(
                                MetricData::getName, metric -> metric));
    }

    private static long longPoint(MetricData metric) {
        return metric.getLongSumData().getPoints().iterator().next().getValue();
    }

    private static final class TestMetricGroup extends UnregisteredMetricsGroup
            implements LogicalScopeProvider {

        @Override
        public String getLogicalScope(CharacterFilter filter) {
            return "taskmanager.job.task.operator";
        }

        @Override
        public String getLogicalScope(CharacterFilter filter, char delimiter) {
            return "taskmanager.job.task.operator";
        }

        @Override
        public MetricGroup getWrappedMetricGroup() {
            return this;
        }

        @Override
        public Map<String, String> getAllVariables() {
            return Map.of("<job_id>", "job-123");
        }
    }

    private static final class FixedHistogramStatistics extends HistogramStatistics {

        @Override
        public double getQuantile(double quantile) {
            return quantile * 10D;
        }

        @Override
        public long[] getValues() {
            return new long[] {5L, 10L, 15L};
        }

        @Override
        public int size() {
            return 3;
        }

        @Override
        public double getMean() {
            return 10D;
        }

        @Override
        public double getStdDev() {
            return 4.08D;
        }

        @Override
        public long getMax() {
            return 15L;
        }

        @Override
        public long getMin() {
            return 5L;
        }
    }

    private static final class RecordingMetricExporter implements MetricExporter {
        private final List<MetricData> exported = new ArrayList<>();
        private boolean shutdown;

        @Override
        public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType) {
            return AggregationTemporality.DELTA;
        }

        @Override
        public CompletableResultCode export(Collection<MetricData> metrics) {
            exported.addAll(metrics);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            shutdown = true;
            return CompletableResultCode.ofSuccess();
        }
    }
}
