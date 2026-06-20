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

import org.springframework.stereotype.Component;

import cn.hutool.http.HttpUtil;

@Component
public class OtelSelfMetricsClient {
    
    private static final int FETCH_TIMEOUT_MILLIS = 2000;
    
    public OtelSelfMetrics fetch(String nodeHost) {
        return parse(HttpUtil.get("http://" + nodeHost + ":8888/metrics", FETCH_TIMEOUT_MILLIS));
    }
    
    OtelSelfMetrics parse(String text) {
        double queueSize = 0;
        double queueCapacity = 0;
        double sentTotal = 0;
        double sendFailedTotal = 0;
        double refusedTotal = 0;
        double processorDroppedTotal = 0;
        for (String line : text.split("\\R")) {
            String sample = line.trim();
            if (sample.isEmpty() || sample.startsWith("#")) {
                continue;
            }
            String[] parts = sample.split("\\s+");
            if (parts.length < 2) {
                continue;
            }
            double value;
            try {
                value = Double.parseDouble(parts[1]);
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (!Double.isFinite(value)) {
                continue;
            }
            String name = parts[0].split("\\{", 2)[0];
            if ("otelcol_exporter_queue_size".equals(name)) {
                queueSize += value;
            } else if ("otelcol_exporter_queue_capacity".equals(name)) {
                queueCapacity += value;
            } else if (name.startsWith("otelcol_exporter_send_failed_")) {
                sendFailedTotal += value;
            } else if (name.startsWith("otelcol_exporter_sent_")) {
                sentTotal += value;
            } else if (name.startsWith("otelcol_receiver_refused_")) {
                refusedTotal += value;
            } else if (name.startsWith("otelcol_processor_dropped_")) {
                processorDroppedTotal += value;
            }
        }
        return new OtelSelfMetrics((long) queueSize, (long) queueCapacity, (long) sentTotal,
                (long) sendFailedTotal, (long) refusedTotal, (long) processorDroppedTotal);
    }
}
