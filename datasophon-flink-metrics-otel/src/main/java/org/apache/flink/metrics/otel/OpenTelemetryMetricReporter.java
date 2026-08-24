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

import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporterBuilder;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.internal.export.MetricProducer;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.flink.metrics.CharacterFilter;
import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.Gauge;
import org.apache.flink.metrics.Histogram;
import org.apache.flink.metrics.LogicalScopeProvider;
import org.apache.flink.metrics.Metric;
import org.apache.flink.metrics.MetricConfig;
import org.apache.flink.metrics.MetricGroup;
import org.apache.flink.metrics.Meter;
import org.apache.flink.metrics.reporter.MetricReporter;
import org.apache.flink.metrics.reporter.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Exports Flink metrics through OpenTelemetry OTLP. */
public class OpenTelemetryMetricReporter extends OpenTelemetryReporterBase
        implements MetricReporter, MetricProducer, Scheduled {

    private static final Logger LOG = LoggerFactory.getLogger(OpenTelemetryMetricReporter.class);
    private static final String LOGICAL_SCOPE_PREFIX = "flink.";

    private final Map<Counter, MetricMetadata> counters = new HashMap<>();
    private final Map<Gauge<?>, MetricMetadata> gauges = new HashMap<>();
    private final Map<Meter, MetricMetadata> meters = new HashMap<>();
    private final Map<Histogram, MetricMetadata> histograms = new HashMap<>();
    private final Clock clock;
    private Map<Metric, Long> lastValueSnapshots = Collections.emptyMap();
    private long lastCollectTimeNanos;
    private volatile CompletableResultCode lastResult;
    private volatile boolean closed;

    public OpenTelemetryMetricReporter() {
        this(Clock.systemUTC());
    }

    OpenTelemetryMetricReporter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public synchronized void open(MetricConfig config) {
        LOG.info("Starting OpenTelemetryMetricReporter");
        super.open(config);
        OtlpGrpcMetricExporterBuilder builder = OtlpGrpcMetricExporter.builder();
        OpenTelemetryReporterOptions.configureEndpoint(config, builder::setEndpoint);
        OpenTelemetryReporterOptions.configureTimeout(config, builder::setTimeout);
        exporter = builder.build();
        // Deltas are reported over [lastCollectTimeNanos, now]. Leaving this at 0 would make the
        // first exported point claim a collection interval starting at 1970-01-01.
        lastCollectTimeNanos = currentTimeNanos();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (exporter == null) {
            return;
        }
        exporter.flush().join(1, TimeUnit.MINUTES);
        CompletableResultCode result = lastResult;
        if (result != null) {
            result.join(1, TimeUnit.MINUTES);
        }
        // The exporter reference is deliberately kept: a report() already queued by Flink's
        // reporter scheduler, which is only shut down after close(), must hit a closed exporter
        // returning a failed result instead of a NullPointerException.
        exporter.close();
    }

    @Override
    public synchronized void notifyOfAddedMetric(
            Metric metric, String metricName, MetricGroup group) {
        MetricMetadata metadata = createMetricMetadata(metricName, group);
        if (metric instanceof Counter) {
            counters.put((Counter) metric, metadata);
        } else if (metric instanceof Gauge) {
            gauges.put((Gauge<?>) metric, metadata);
        } else if (metric instanceof Meter) {
            meters.put((Meter) metric, metadata);
        } else if (metric instanceof Histogram) {
            histograms.put((Histogram) metric, metadata);
        } else {
            LOG.warn(
                    "Cannot add metric {} of unsupported type {}",
                    metricName,
                    metric.getClass().getName());
        }
    }

    @Override
    public synchronized void notifyOfRemovedMetric(
            Metric metric, String metricName, MetricGroup group) {
        counters.remove(metric);
        gauges.remove(metric);
        meters.remove(metric);
        histograms.remove(metric);
        lastValueSnapshots.remove(metric);
    }

    @Override
    public synchronized Collection<MetricData> collectAllMetrics() {
        long currentTimeNanos = currentTimeNanos();
        OpenTelemetryMetricAdapter.CollectionMetadata collection =
                new OpenTelemetryMetricAdapter.CollectionMetadata(
                        resource, lastCollectTimeNanos, currentTimeNanos);
        Map<Metric, Long> currentValueSnapshots = new HashMap<>();
        Collection<MetricData> data = new ArrayList<>();
        counters.forEach(
                (counter, metadata) -> {
                    long current = counter.getCount();
                    currentValueSnapshots.put(counter, current);
                    OpenTelemetryMetricAdapter.convertCounter(
                                    collection,
                                    current,
                                    lastValueSnapshots.getOrDefault(counter, 0L),
                                    metadata)
                            .ifPresent(data::add);
                });
        gauges.forEach(
                (gauge, metadata) ->
                        OpenTelemetryMetricAdapter.convertGauge(collection, gauge, metadata)
                                .ifPresent(data::add));
        meters.forEach(
                (meter, metadata) -> {
                    long current = meter.getCount();
                    currentValueSnapshots.put(meter, current);
                    data.addAll(
                            OpenTelemetryMetricAdapter.convertMeter(
                                    collection,
                                    meter,
                                    current,
                                    lastValueSnapshots.getOrDefault(meter, 0L),
                                    metadata));
                });
        histograms.forEach(
                (histogram, metadata) ->
                        data.add(
                                OpenTelemetryMetricAdapter.convertHistogram(
                                        collection, histogram, metadata)));
        lastValueSnapshots = currentValueSnapshots;
        lastCollectTimeNanos = currentTimeNanos;
        return data;
    }

    @Override
    public void report() {
        MetricExporter currentExporter = exporter;
        if (closed || currentExporter == null) {
            return;
        }
        Collection<MetricData> metrics = collectAllMetrics();
        try {
            CompletableResultCode result = currentExporter.export(metrics);
            lastResult = result;
            result.whenComplete(
                    () -> {
                        if (!result.isSuccess()) {
                            LOG.warn("Failed to export {} Flink metrics through OTLP", metrics.size());
                        }
                    });
        } catch (RuntimeException e) {
            LOG.error("Failed to export {} Flink metrics through OTLP", metrics.size(), e);
        }
    }

    private long currentTimeNanos() {
        Instant now = clock.instant();
        return TimeUnit.SECONDS.toNanos(now.getEpochSecond()) + now.getNano();
    }

    private static MetricMetadata createMetricMetadata(String metricName, MetricGroup group) {
        String name =
                LOGICAL_SCOPE_PREFIX
                        + LogicalScopeProvider.castFrom(group)
                                .getLogicalScope(CharacterFilter.NO_OP_FILTER)
                        + "."
                        + metricName;
        Map<String, String> variables =
                group.getAllVariables().entrySet().stream()
                        .collect(
                                Collectors.toMap(
                                        entry -> VariableNameUtil.getVariableName(entry.getKey()),
                                        Map.Entry::getValue));
        return new MetricMetadata(name, variables);
    }
}
