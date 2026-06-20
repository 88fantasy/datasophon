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

import com.datasophon.api.service.ClusterAlertHistoryService;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.dao.entity.ClusterInfoEntity;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class OtelAlertSchedulerTest {

    @Test
    void emitsOneFiringAlertUntilQueueWatermarkResolves() {
        AtomicReference<List<NodeOtelMetrics>> metrics = new AtomicReference<>(List.of(
                healthy("worker-1", new OtelSelfMetrics(90, 100, 100, 0, 0, 0))));
        List<String> alerts = new ArrayList<>();
        OtelAlertScheduler scheduler = scheduler(metrics, alerts);

        scheduler.checkCollectors();
        scheduler.checkCollectors();
        metrics.set(List.of(healthy("worker-1", new OtelSelfMetrics(10, 100, 100, 0, 0, 0))));
        scheduler.checkCollectors();

        assertThat(alerts).hasSize(2);
        assertThat(alerts.get(0)).contains("\"status\":\"firing\"")
                .contains("OtelCollectorQueueHigh");
        assertThat(alerts.get(1)).contains("\"status\":\"resolved\"")
                .contains("OtelCollectorQueueHigh");
    }

    @Test
    void emitsSendFailureAlertOnlyAboveConfiguredRate() {
        AtomicReference<List<NodeOtelMetrics>> metrics = new AtomicReference<>(List.of(
                healthy("worker-1", new OtelSelfMetrics(0, 100, 90, 10, 0, 0))));
        List<String> alerts = new ArrayList<>();
        OtelAlertScheduler scheduler = scheduler(metrics, alerts);

        scheduler.checkCollectors();

        assertThat(alerts).singleElement().asString()
                .contains("OtelCollectorSendFailureRateHigh")
                .contains("\"status\":\"firing\"");
    }

    private static OtelAlertScheduler scheduler(AtomicReference<List<NodeOtelMetrics>> metrics,
                                                List<String> alerts) {
        OtelMonitorService monitor = new OtelMonitorService(null, null) {
            @Override
            public List<NodeOtelMetrics> collectAll(Integer clusterId) {
                return metrics.get();
            }
        };
        ClusterInfoService clusters = proxy(ClusterInfoService.class, (method, args) -> {
            if ("runningClusterList".equals(method)) {
                ClusterInfoEntity cluster = new ClusterInfoEntity();
                cluster.setId(7);
                return List.of(cluster);
            }
            return null;
        });
        ClusterAlertHistoryService history = proxy(ClusterAlertHistoryService.class, (method, args) -> {
            if ("saveAlertHistory".equals(method)) {
                alerts.add((String) args[0]);
            }
            return null;
        });
        return new OtelAlertScheduler(monitor, clusters, history, 0.8d, 0.05d);
    }

    private static NodeOtelMetrics healthy(String hostname, OtelSelfMetrics metrics) {
        return new NodeOtelMetrics(hostname, true, null, metrics);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> invocation.invoke(method.getName(), args));
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(String method, Object[] args);
    }
}
