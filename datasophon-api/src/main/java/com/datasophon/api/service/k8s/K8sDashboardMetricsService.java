package com.datasophon.api.service.k8s;

import com.datasophon.api.observability.OtelDorisReaderFactory;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** 从 OTel Doris 指标表读取 K8s 节点使用量。 */
@Service
public class K8sDashboardMetricsService {
    private static final String NODE_NAME = "CAST(resource_attributes['k8s.node.name'] AS STRING)";

    private final OtelDorisReaderFactory readerFactory;

    public K8sDashboardMetricsService(OtelDorisReaderFactory readerFactory) {
        this.readerFactory = readerFactory;
    }

    public Snapshot snapshot(Integer clusterId) {
        JdbcClient client = readerFactory.create(clusterId);
        String marker = marker(clusterId);
        Snapshot snapshot = query(client, marker);
        return snapshot.hasData() ? snapshot : query(client, localMarker());
    }

    public List<TrendSample> trends(Integer clusterId, long seconds) {
        JdbcClient client = readerFactory.create(clusterId);
        String marker = query(client, marker(clusterId)).hasData() ? marker(clusterId) : localMarker();
        Map<Long, Double> cpu = trend(client, "k8s.node.cpu.usage", marker, seconds);
        Map<Long, Double> memory = trend(client, "k8s.node.memory.working_set", marker, seconds);
        Map<Long, TrendSample> samples = new LinkedHashMap<>();
        cpu.forEach((timestamp, value) -> samples.put(timestamp, new TrendSample(timestamp, value, null)));
        memory.forEach((timestamp, value) -> samples.merge(timestamp, new TrendSample(timestamp, null, value),
                (left, right) -> new TrendSample(timestamp, left.cpuCores(), right.memoryBytes())));
        return samples.values().stream().sorted(java.util.Comparator.comparingLong(TrendSample::timestamp)).toList();
    }

    public Map<String, NamespaceUsage> namespaceUsage(Integer clusterId) {
        JdbcClient client = readerFactory.create(clusterId);
        String marker = query(client, marker(clusterId)).hasData() ? marker(clusterId) : localMarker();
        Map<String, Double> cpu = namespaceMetric(client, "k8s.pod.cpu.usage", marker);
        Map<String, Double> memory = namespaceMetric(client, "k8s.pod.memory.working_set", marker);
        Map<String, NamespaceUsage> result = new LinkedHashMap<>();
        cpu.forEach((namespace, value) -> result.put(namespace, new NamespaceUsage(value, null)));
        memory.forEach((namespace, value) -> result.merge(namespace, new NamespaceUsage(null, value),
                (left, right) -> new NamespaceUsage(left.cpuCores(), right.memoryBytes())));
        return result;
    }

    public Map<String, NodeUsage> nodeUsage(Integer clusterId) {
        JdbcClient client = readerFactory.create(clusterId);
        String marker = query(client, marker(clusterId)).hasData() ? marker(clusterId) : localMarker();
        Map<String, Double> cpu = namedMetric(client, "k8s.node.cpu.usage", marker, "k8s.node.name");
        Map<String, Double> memory = namedMetric(client, "k8s.node.memory.working_set", marker, "k8s.node.name");
        Map<String, Double> disk = namedMetric(client, "k8s.node.filesystem.usage", marker, "k8s.node.name");
        Map<String, NodeUsage> result = new LinkedHashMap<>();
        cpu.forEach((name, value) -> result.put(name, new NodeUsage(value, null, null)));
        memory.forEach((name, value) -> result.merge(name, new NodeUsage(null, value, null),
                (left, right) -> new NodeUsage(left.cpuCores(), right.memoryBytes(), left.diskBytes())));
        disk.forEach((name, value) -> result.merge(name, new NodeUsage(null, null, value),
                (left, right) -> new NodeUsage(left.cpuCores(), left.memoryBytes(), right.diskBytes())));
        return result;
    }

    private Snapshot query(JdbcClient client, String marker) {
        return new Snapshot(latest(client, "k8s.node.cpu.usage", marker),
                latest(client, "k8s.node.memory.working_set", marker),
                latest(client, "k8s.node.filesystem.usage", marker),
                latest(client, "k8s.node.filesystem.capacity", marker));
    }

    private Double latest(JdbcClient client, String metric, String marker) {
        String sql = "SELECT SUM(value) AS value FROM ("
                + "SELECT value, ROW_NUMBER() OVER (PARTITION BY " + NODE_NAME + " ORDER BY timestamp DESC) AS rn "
                + "FROM otel.otel_metrics_gauge WHERE metric_name = :metric "
                + "AND CAST(resource_attributes AS STRING) LIKE :marker "
                + "AND timestamp >= FROM_UNIXTIME(UNIX_TIMESTAMP() - 300)) samples WHERE rn = 1";
        List<Map<String, Object>> rows = client.sql(sql).param("metric", metric).param("marker", marker).query().listOfRows();
        if (rows.isEmpty() || rows.get(0).get("value") == null) {
            return null;
        }
        return ((Number) rows.get(0).get("value")).doubleValue();
    }

    private Map<Long, Double> trend(JdbcClient client, String metric, String marker, long seconds) {
        String sql = "SELECT bucket, SUM(node_value) AS value FROM ("
                + "SELECT " + NODE_NAME + " AS node, FLOOR(UNIX_TIMESTAMP(timestamp) / 60) * 60 AS bucket, AVG(value) AS node_value "
                + "FROM otel.otel_metrics_gauge WHERE metric_name = :metric "
                + "AND CAST(resource_attributes AS STRING) LIKE :marker "
                + "AND timestamp >= FROM_UNIXTIME(UNIX_TIMESTAMP() - :seconds) GROUP BY node, bucket"
                + ") samples GROUP BY bucket ORDER BY bucket";
        return client.sql(sql).param("metric", metric).param("marker", marker).param("seconds", seconds).query()
                .listOfRows().stream().collect(Collectors.toMap(
                        row -> ((Number) row.get("bucket")).longValue(),
                        row -> ((Number) row.get("value")).doubleValue(), (left, right) -> right, LinkedHashMap::new));
    }

    private Map<String, Double> namespaceMetric(JdbcClient client, String metric, String marker) {
        return namedMetric(client, metric, marker, "k8s.namespace.name", "k8s.pod.uid");
    }

    private Map<String, Double> namedMetric(JdbcClient client, String metric, String marker, String key) {
        return namedMetric(client, metric, marker, key, key);
    }

    private Map<String, Double> namedMetric(JdbcClient client, String metric, String marker, String key, String identity) {
        String name = "CAST(resource_attributes['" + key + "'] AS STRING)";
        String identityName = "CAST(resource_attributes['" + identity + "'] AS STRING)";
        String sql = "SELECT name, SUM(value) AS value FROM (SELECT " + name + " AS name, value, "
                + "ROW_NUMBER() OVER (PARTITION BY " + name + ", " + identityName + " ORDER BY timestamp DESC) AS rn "
                + "FROM otel.otel_metrics_gauge WHERE metric_name = :metric "
                + "AND CAST(resource_attributes AS STRING) LIKE :marker "
                + "AND timestamp >= FROM_UNIXTIME(UNIX_TIMESTAMP() - 300)) samples WHERE rn = 1 GROUP BY name";
        return client.sql(sql).param("metric", metric).param("marker", marker).query().listOfRows().stream()
                .collect(Collectors.toMap(row -> String.valueOf(row.get("name")),
                        row -> ((Number) row.get("value")).doubleValue(), (left, right) -> right, LinkedHashMap::new));
    }

    private String marker(Integer clusterId) {
        return "%\"datasophon.cluster.id\":\"" + clusterId + "\"%";
    }

    private String localMarker() {
        return "%\"datasophon.cluster.id\":\"local\"%";
    }

    public record Snapshot(Double cpuCores, Double memoryBytes, Double diskBytes, Double diskCapacityBytes) {
        boolean hasData() {
            return cpuCores != null || memoryBytes != null || diskBytes != null;
        }
    }

    public record TrendSample(long timestamp, Double cpuCores, Double memoryBytes) {
    }

    public record NamespaceUsage(Double cpuCores, Double memoryBytes) {
    }

    public record NodeUsage(Double cpuCores, Double memoryBytes, Double diskBytes) {
    }
}
