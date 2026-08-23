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

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.ValueAtQuantile;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableDoublePointData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableGaugeData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableLongPointData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableMetricData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableSumData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableSummaryData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableSummaryPointData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableValueAtQuantile;
import io.opentelemetry.sdk.resources.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.flink.metrics.Gauge;
import org.apache.flink.metrics.Histogram;
import org.apache.flink.metrics.HistogramStatistics;
import org.apache.flink.metrics.Meter;

/** Converts Flink metrics to OpenTelemetry metric data. */
final class OpenTelemetryMetricAdapter {

    private static final InstrumentationScopeInfo INSTRUMENTATION_SCOPE_INFO =
            InstrumentationScopeInfo.create("io.confluent.flink.common.metrics");

    private OpenTelemetryMetricAdapter() {}

    static Optional<MetricData> convertCounter(
            CollectionMetadata collection,
            long count,
            long previousCount,
            MetricMetadata metadata) {
        long delta = count - previousCount;
        if (delta < 0) {
            return Optional.empty();
        }
        return Optional.of(
                ImmutableMetricData.createLongSum(
                        collection.resource,
                        INSTRUMENTATION_SCOPE_INFO,
                        metadata.getName(),
                        "",
                        "",
                        ImmutableSumData.create(
                                true,
                                AggregationTemporality.DELTA,
                                Collections.singleton(
                                        ImmutableLongPointData.create(
                                                collection.startEpochNanos,
                                                collection.epochNanos,
                                                convertVariables(metadata.getVariables()),
                                                delta)))));
    }

    static Optional<MetricData> convertGauge(
            CollectionMetadata collection, Gauge<?> gauge, MetricMetadata metadata) {
        Object value = gauge.getValue();
        if (!(value instanceof Number)) {
            return Optional.empty();
        }
        Number number = (Number) value;
        if (number instanceof Long || number instanceof Integer) {
            return Optional.of(
                    ImmutableMetricData.createLongGauge(
                            collection.resource,
                            INSTRUMENTATION_SCOPE_INFO,
                            metadata.getName(),
                            "",
                            "",
                            ImmutableGaugeData.create(
                                    Collections.singleton(
                                            ImmutableLongPointData.create(
                                                    collection.startEpochNanos,
                                                    collection.epochNanos,
                                                    convertVariables(metadata.getVariables()),
                                                    number.longValue())))));
        }
        return Optional.of(
                ImmutableMetricData.createDoubleGauge(
                        collection.resource,
                        INSTRUMENTATION_SCOPE_INFO,
                        metadata.getName(),
                        "",
                        "",
                        ImmutableGaugeData.create(
                                Collections.singleton(
                                        ImmutableDoublePointData.create(
                                                collection.startEpochNanos,
                                                collection.epochNanos,
                                                convertVariables(metadata.getVariables()),
                                                number.doubleValue())))));
    }

    static List<MetricData> convertMeter(
            CollectionMetadata collection,
            Meter meter,
            long count,
            long previousCount,
            MetricMetadata metadata) {
        List<MetricData> metrics = new ArrayList<>();
        convertCounter(collection, count, previousCount, metadata.subMetric("count"))
                .ifPresent(metrics::add);
        convertGauge(collection, meter::getRate, metadata.subMetric("rate"))
                .ifPresent(metrics::add);
        return metrics;
    }

    static MetricData convertHistogram(
            CollectionMetadata collection, Histogram histogram, MetricMetadata metadata) {
        HistogramStatistics statistics = histogram.getStatistics();
        List<ValueAtQuantile> quantiles = new ArrayList<>();
        quantiles.add(ImmutableValueAtQuantile.create(0D, statistics.getMin()));
        for (double quantile : new double[] {0.5D, 0.75D, 0.95D, 0.99D}) {
            quantiles.add(ImmutableValueAtQuantile.create(quantile, statistics.getQuantile(quantile)));
        }
        quantiles.add(ImmutableValueAtQuantile.create(1D, statistics.getMax()));
        return ImmutableMetricData.createDoubleSummary(
                collection.resource,
                INSTRUMENTATION_SCOPE_INFO,
                metadata.getName(),
                "",
                "",
                ImmutableSummaryData.create(
                        Collections.singleton(
                                ImmutableSummaryPointData.create(
                                        collection.startEpochNanos,
                                        collection.epochNanos,
                                        convertVariables(metadata.getVariables()),
                                        histogram.getCount(),
                                        statistics.getMean() * histogram.getCount(),
                                        quantiles))));
    }

    private static Attributes convertVariables(Map<String, String> variables) {
        AttributesBuilder builder = Attributes.builder();
        variables.forEach(builder::put);
        return builder.build();
    }

    static final class CollectionMetadata {
        private final Resource resource;
        private final long startEpochNanos;
        private final long epochNanos;

        CollectionMetadata(Resource resource, long startEpochNanos, long epochNanos) {
            this.resource = resource;
            this.startEpochNanos = startEpochNanos;
            this.epochNanos = epochNanos;
        }
    }
}
