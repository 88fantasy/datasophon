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

package com.datasophon.api.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OtelSelfMetricsClientTest {

    private final OtelSelfMetricsClient client = new OtelSelfMetricsClient();

    @Test
    void parsesAndAggregatesCollectorMetrics() {
        String text = """
                # HELP otelcol_exporter_queue_size Current size of the retry queue.
                otelcol_exporter_queue_size{exporter="awss3/metrics"} 2
                otelcol_exporter_queue_size{exporter="awss3/traces"} 3
                otelcol_exporter_queue_capacity{exporter="awss3/metrics"} 10
                otelcol_exporter_queue_capacity{exporter="awss3/traces"} 20
                otelcol_exporter_sent_spans_total{exporter="awss3/traces"} 11
                otelcol_exporter_sent_metric_points_total{exporter="awss3/metrics"} 13
                otelcol_exporter_send_failed_spans_total{exporter="awss3/traces"} 5
                otelcol_receiver_refused_log_records_total{receiver="otlp"} 7
                otelcol_processor_dropped_spans_total{processor="memory_limiter"} 9
                """;

        OtelSelfMetrics metrics = client.parse(text);

        assertThat(metrics.queueSize()).isEqualTo(5);
        assertThat(metrics.queueCapacity()).isEqualTo(30);
        assertThat(metrics.sentTotal()).isEqualTo(24);
        assertThat(metrics.sendFailedTotal()).isEqualTo(5);
        assertThat(metrics.refusedTotal()).isEqualTo(7);
        assertThat(metrics.processorDroppedTotal()).isEqualTo(9);
    }

    @Test
    void ignoresCommentsMalformedLinesAndMissingMetrics() {
        OtelSelfMetrics metrics = client.parse("# comment\ninvalid\notelcol_exporter_queue_size NaN\n");

        assertThat(metrics).isEqualTo(new OtelSelfMetrics(0, 0, 0, 0, 0, 0));
    }
}
