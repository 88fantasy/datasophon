package com.datasophon.api.service.k8s;

import com.datasophon.api.observability.OtelDorisReaderFactory;

import java.util.LinkedHashMap;
import java.util.List;
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
        String marker = resolveMarker(client, clusterId);
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
        String marker = resolveMarker(client, clusterId);
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
        String marker = resolveMarker(client, clusterId);
        Map<String, Double> cpu = namedMetric(client, "k8s.node.cpu.usage", marker, attribute("k8s.node.name"));
        Map<String, Double> memory = namedMetric(client, "k8s.node.memory.working_set", marker,
                attribute("k8s.node.name"));
        Map<String, Double> disk = namedMetric(client, "k8s.node.filesystem.usage", marker,
                attribute("k8s.node.name"));
        Map<String, NodeUsage> result = new LinkedHashMap<>();
        cpu.forEach((name, value) -> result.put(name, new NodeUsage(value, null, null)));
        memory.forEach((name, value) -> result.merge(name, new NodeUsage(null, value, null),
                (left, right) -> new NodeUsage(left.cpuCores(), right.memoryBytes(), left.diskBytes())));
        disk.forEach((name, value) -> result.merge(name, new NodeUsage(null, null, value),
                (left, right) -> new NodeUsage(left.cpuCores(), left.memoryBytes(), right.diskBytes())));
        return result;
    }

    /**
     * 按节点取「内存用量 / 磁盘用量 / 1 分钟平均负载」，供主机列表展示。
     *
     * <p>与 {@link #nodeUsage(Integer)} 的区别是多带一个平均负载，而负载来自 hostmetrics receiver、
     * **没有 {@code k8s.node.name} 资源属性**（实测为 NULL），改按 {@code service_instance_id} 取。
     * <b>注意：memory/disk 按 {@code k8s.node.name} 归键，
     * load 按 {@code service_instance_id}（节点 IP）归键，节点名与 IP 不同的集群上二者是不同的 key</b>——
     * 返回的 Map 里同一节点可能拆成两条记录（hostname 一条含 memory/disk，ip 一条含 load）。
     * 只有节点名恰好等于 IP 时两侧键才会重合成一条记录；调用方（{@code HostResponse#applyUsage}）
     * 按字段分别在 hostname/ip 两个 key 下回落取值，不能假设一次 {@code get(key)} 就能拿全三项指标。
     *
     * @param clusterId 集群 ID
     * @return 节点标识（IP / 节点名）→ 用量；查询失败返回空 Map，由调用方降级
     */
    public Map<String, HostUsage> hostUsage(Integer clusterId) {
        JdbcClient client = readerFactory.create(clusterId);
        String marker = resolveMarker(client, clusterId);
        Map<String, Double> memory = namedMetric(client, "k8s.node.memory.working_set", marker,
                attribute("k8s.node.name"));
        Map<String, Double> disk = namedMetric(client, "k8s.node.filesystem.usage", marker,
                attribute("k8s.node.name"));
        Map<String, Double> load = namedMetric(client, "system.cpu.load_average.1m", marker,
                "service_instance_id");

        Map<String, HostUsage> result = new LinkedHashMap<>();
        memory.forEach((name, value) -> result.put(name, new HostUsage(value, null, null)));
        disk.forEach((name, value) -> result.merge(name, new HostUsage(null, value, null),
                (left, right) -> new HostUsage(left.memoryBytes(), right.diskBytes(), left.load1m())));
        load.forEach((name, value) -> result.merge(name, new HostUsage(null, null, value),
                (left, right) -> new HostUsage(left.memoryBytes(), left.diskBytes(), right.load1m())));
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
        return namedMetric(client, metric, marker, attribute("k8s.namespace.name"), attribute("k8s.pod.uid"));
    }

    private Map<String, Double> namedMetric(JdbcClient client, String metric, String marker, String expression) {
        return namedMetric(client, metric, marker, expression, expression);
    }

    /** 按 SQL 表达式分组取最新指标；K8s 资源属性与 {@code service_instance_id} 共用同一模板。 */
    private Map<String, Double> namedMetric(JdbcClient client, String metric, String marker,
                                            String nameExpression, String identityExpression) {
        String sql = "SELECT name, SUM(value) AS value FROM (SELECT " + nameExpression + " AS name, value, "
                + "ROW_NUMBER() OVER (PARTITION BY " + nameExpression + ", " + identityExpression + " ORDER BY timestamp DESC) AS rn "
                + "FROM otel.otel_metrics_gauge WHERE metric_name = :metric "
                + "AND CAST(resource_attributes AS STRING) LIKE :marker "
                + "AND timestamp >= FROM_UNIXTIME(UNIX_TIMESTAMP() - 300)) samples WHERE rn = 1 GROUP BY name";
        return client.sql(sql).param("metric", metric).param("marker", marker).query().listOfRows().stream()
                .collect(Collectors.toMap(row -> String.valueOf(row.get("name")),
                        row -> ((Number) row.get("value")).doubleValue(), (left, right) -> right, LinkedHashMap::new));
    }

    private static String attribute(String key) {
        return "CAST(resource_attributes['" + key + "'] AS STRING)";
    }

    /**
     * 判定该集群的指标是挂在自己的 marker 下，还是落在 {@code local} marker 下。
     *
     * <p>原先复用 {@link #query} 做这个判定：跑 4 条带 {@code ROW_NUMBER() OVER (PARTITION BY ...)}
     * 的窗口函数查询，再把整个 Snapshot 丢掉、只取一个布尔。这里换成一条 {@code LIMIT 1} 的存在性
     * 查询，语义与 {@link Snapshot#hasData()} 一致——只要 cpu/memory/disk 三个指标里任一在窗口内
     * 有非空值即算有数据（{@code hasData()} 同样不看 {@code diskCapacityBytes}）。
     */
    private String resolveMarker(JdbcClient client, Integer clusterId) {
        String own = marker(clusterId);
        return hasData(client, own) ? own : localMarker();
    }

    private boolean hasData(JdbcClient client, String marker) {
        String sql = "SELECT 1 FROM otel.otel_metrics_gauge "
                + "WHERE metric_name IN ('k8s.node.cpu.usage', 'k8s.node.memory.working_set', "
                + "'k8s.node.filesystem.usage') "
                + "AND CAST(resource_attributes AS STRING) LIKE :marker "
                + "AND timestamp >= FROM_UNIXTIME(UNIX_TIMESTAMP() - 300) "
                + "AND value IS NOT NULL LIMIT 1";
        return !client.sql(sql).param("marker", marker).query().listOfRows().isEmpty();
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

    /** 主机列表用的单节点用量。 */
    public record HostUsage(Double memoryBytes, Double diskBytes, Double load1m) {
    }
}
