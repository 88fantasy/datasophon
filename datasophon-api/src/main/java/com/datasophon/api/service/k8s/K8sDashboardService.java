package com.datasophon.api.service.k8s;

import com.datasophon.api.dto.v2.K8sDashboardResponse;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.api.vo.k8s.K8sWorkloadInfo;
import com.datasophon.common.k8s.vo.k8s.K8sEvent;
import com.datasophon.common.k8s.vo.k8s.K8sNode;
import com.datasophon.common.k8s.vo.k8s.K8sPod;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

/** 读取 K8s API 的实时概览；OTel 时序尚未就绪时明确返回不可用状态。 */
@Service
public class K8sDashboardService {
    private final K8sClusterConfigService configService;
    private final K8sService k8sService;
    private final K8sDashboardMetricsService metricsService;

    public K8sDashboardService(K8sClusterConfigService configService, K8sService k8sService,
                               K8sDashboardMetricsService metricsService) {
        this.configService = configService;
        this.k8sService = k8sService;
        this.metricsService = metricsService;
    }

    public K8sDashboardResponse getDashboard(Integer clusterId, String range) {
        K8sClusterConfig config = configService.getInitConfig(clusterId);
        List<K8sNode> nodes = k8sService.listNodes(config);
        List<K8sPod> pods = k8sService.listAllPods(config);
        List<K8sEvent> k8sEvents;
        try {
            k8sEvents = k8sService.listAllEvents(config);
        } catch (Exception ignored) {
            k8sEvents = List.of();
        }
        List<K8sWorkloadInfo> workloadResources = k8sService.listAllWorkloads(config);
        Map<String, Integer> podCount = pods.stream()
                .filter(pod -> pod.getSpec() != null && pod.getSpec().getNodeName() != null)
                .collect(Collectors.groupingBy(pod -> pod.getSpec().getNodeName(), Collectors.summingInt(pod -> 1)));

        int readyNodes = (int) nodes.stream().filter(this::isReady).count();
        int runningPods = (int) pods.stream().filter(pod -> pod.getStatus() != null
                && "Running".equals(pod.getStatus().getPhase())).count();
        int critical = (int) nodes.stream().filter(node -> !isReady(node)).count();
        int warning = (int) pods.stream().filter(pod -> pod.getStatus() != null
                && "Pending".equals(pod.getStatus().getPhase())).count();
        String health = critical > 0 ? "CRITICAL" : warning > 0 ? "WARNING" : "HEALTHY";

        List<K8sDashboardResponse.Node> nodeRows = nodes.stream()
                .map(node -> K8sDashboardResponse.Node.builder()
                        .name(node.getMetadata() == null ? "-" : node.getMetadata().getName())
                        .status(isReady(node) ? "READY" : "NOT_READY")
                        .podCount(podCount.getOrDefault(node.getMetadata() == null ? null : node.getMetadata().getName(), 0))
                        .podCapacity(nodePodCapacity(node))
                        .build())
                .toList();

        int podCapacity = nodes.stream().mapToInt(this::nodePodCapacity).sum();
        List<K8sDashboardResponse.Capacity> capacities = List.of(
                capacity("CPU 使用率", "cpu", nodes),
                capacity("内存使用率", "memory", nodes),
                capacity("磁盘使用率", "ephemeral-storage", nodes),
                K8sDashboardResponse.Capacity.builder().name("Pod 容量")
                        .percent(podCapacity == 0 ? null : pods.size() * 100D / podCapacity)
                        .used((double) pods.size()).total((double) podCapacity).unit("count").build());
        K8sDashboardMetricsService.Snapshot metrics = null;
        try {
            metrics = metricsService.snapshot(clusterId);
            applyUsage(capacities, metrics, nodes);
        } catch (Exception ignored) {
            // Doris 不可用时保留 K8s API 提供的容量信息。
        }
        if (metrics != null) {
            Map<String, K8sDashboardMetricsService.NodeUsage> nodeUsage = metricsService.nodeUsage(clusterId);
            Map<String, K8sNode> nodeByName = nodes.stream().filter(node -> node.getMetadata() != null)
                    .collect(Collectors.toMap(node -> node.getMetadata().getName(), node -> node));
            nodeRows = nodeRows.stream().map(row -> {
                K8sDashboardMetricsService.NodeUsage usage = nodeUsage.get(row.getName());
                K8sNode node = nodeByName.get(row.getName());
                if (usage == null || node == null) {
                    return row;
                }
                return K8sDashboardResponse.Node.builder().name(row.getName()).status(row.getStatus())
                        .podCount(row.getPodCount()).podCapacity(row.getPodCapacity())
                        .cpuPercent(cpuPercent(usage.cpuCores(), resourceTotal("cpu", List.of(node))))
                        .memoryPercent(percent(usage.memoryBytes(), resourceTotal("memory", List.of(node))))
                        .diskPercent(percent(usage.diskBytes(), resourceTotal("ephemeral-storage", List.of(node)))).build();
            }).toList();
        }
        List<K8sDashboardResponse.TrendPoint> trends = metrics == null ? List.of()
                : metricsService.trends(clusterId, rangeSeconds(range)).stream()
                .map(point -> K8sDashboardResponse.TrendPoint.builder()
                        .timestamp(Instant.ofEpochSecond(point.timestamp()))
                        .cpuPercent(cpuPercent(point.cpuCores(), resourceTotal("cpu", nodes)))
                        .memoryPercent(percent(point.memoryBytes(), resourceTotal("memory", nodes))).build())
                .toList();
        Map<String, K8sDashboardMetricsService.NamespaceUsage> namespaceUsage = metrics == null ? Map.of()
                : metricsService.namespaceUsage(clusterId);

        Map<String, Integer> namespacePods = pods.stream()
                .filter(pod -> pod.getMetadata() != null && pod.getMetadata().getNamespace() != null)
                .collect(Collectors.groupingBy(pod -> pod.getMetadata().getNamespace(), Collectors.summingInt(pod -> 1)));
        List<K8sDashboardResponse.Namespace> namespaces = namespacePods.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .map(entry -> K8sDashboardResponse.Namespace.builder().name(entry.getKey())
                        .podCount(entry.getValue())
                        .cpuCores(namespaceUsage.containsKey(entry.getKey()) ? namespaceUsage.get(entry.getKey()).cpuCores() : null)
                        .memoryBytes(namespaceUsage.containsKey(entry.getKey()) ? namespaceUsage.get(entry.getKey()).memoryBytes() : null)
                        .build())
                .toList();

        List<K8sDashboardResponse.Workload> workloads = workloadResources.stream()
                .map(workload -> K8sDashboardResponse.Workload.builder().name(workload.getName())
                        .namespace(workload.getNamespace()).type(workload.getType()).ready(workload.getReady())
                        .desired(workload.getDesired())
                        .status(workload.getReady() != null && workload.getReady().equals(workload.getDesired()) ? "NORMAL" : "WARNING")
                        .build())
                .sorted(Comparator.comparing(K8sDashboardResponse.Workload::getStatus)
                        .thenComparing(K8sDashboardResponse.Workload::getName))
                .limit(10)
                .toList();
        List<K8sDashboardResponse.Event> events = k8sEvents.stream()
                .map(event -> K8sDashboardResponse.Event.builder()
                        .type(event.getType())
                        .reason(event.getReason())
                        .namespace(event.getMetadata() == null ? "-" : event.getMetadata().getNamespace())
                        .object(eventObject(event))
                        .message(event.getMessage())
                        .lastTimestamp(eventTimestamp(event))
                        .build())
                .sorted(Comparator.comparing(K8sDashboardResponse.Event::getLastTimestamp,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(20)
                .toList();

        return K8sDashboardResponse.builder()
                .observedAt(Instant.now())
                .telemetry(K8sDashboardResponse.Telemetry.builder()
                        .status(metrics != null && metrics.hasData() ? "READY" : "UNAVAILABLE")
                        .message(metrics == null || !metrics.hasData() ? "OTel 指标暂不可用，当前仅展示 K8s API 实时状态。" : null).build())
                .overview(K8sDashboardResponse.Overview.builder().health(health)
                        .readyNodes(readyNodes).totalNodes(nodes.size()).runningPods(runningPods)
                        .totalPods(pods.size()).critical(critical).warning(warning).build())
                .capacities(capacities)
                .trends(trends)
                .namespaces(namespaces)
                .workloads(workloads)
                .nodes(nodeRows)
                .events(events)
                .build();
    }

    private boolean isReady(K8sNode node) {
        return node.getStatus() != null && node.getStatus().getConditions() != null
                && node.getStatus().getConditions().stream()
                .anyMatch(condition -> "Ready".equals(condition.getType()) && "True".equals(condition.getStatus()));
    }

    private String eventObject(K8sEvent event) {
        if (event.getInvolvedObject() == null) {
            return "-";
        }
        String kind = event.getInvolvedObject().getKind();
        String name = event.getInvolvedObject().getName();
        return kind == null || name == null ? "-" : kind + "/" + name;
    }

    private String eventTimestamp(K8sEvent event) {
        if (event.getLastTimestamp() != null) {
            return event.getLastTimestamp();
        }
        if (event.getEventTime() != null) {
            return event.getEventTime();
        }
        return event.getMetadata() == null ? null : event.getMetadata().getCreationTimestamp();
    }

    private K8sDashboardResponse.Capacity capacity(String name, String resource, List<K8sNode> nodes) {
        long total = resourceTotal(resource, nodes);
        return K8sDashboardResponse.Capacity.builder().name(name)
                .total(total == 0 ? null : "cpu".equals(resource) ? total / 1000D : (double) total)
                .unit("cpu".equals(resource) ? "core" : "byte").build();
    }

    private long resourceTotal(String resource, List<K8sNode> nodes) {
        return nodes.stream().map(K8sNode::getStatus).filter(java.util.Objects::nonNull)
                .map(K8sNode.NodeStatus::getAllocatable).filter(java.util.Objects::nonNull)
                .map(values -> values.get(resource)).filter(java.util.Objects::nonNull)
                .mapToLong(value -> "cpu".equals(resource) ? cpuMillis(value) : bytes(value)).sum();
    }

    private void applyUsage(List<K8sDashboardResponse.Capacity> capacities,
                            K8sDashboardMetricsService.Snapshot metrics, List<K8sNode> nodes) {
        long cpuTotal = resourceTotal("cpu", nodes);
        long memoryTotal = resourceTotal("memory", nodes);
        if (metrics.cpuCores() != null) {
            capacities.get(0).setUsed(metrics.cpuCores());
            capacities.get(0).setPercent(cpuTotal == 0 ? null : metrics.cpuCores() * 100000D / cpuTotal);
        }
        if (metrics.memoryBytes() != null) {
            capacities.get(1).setUsed(metrics.memoryBytes());
            capacities.get(1).setPercent(memoryTotal == 0 ? null : metrics.memoryBytes() * 100D / memoryTotal);
        }
        if (metrics.diskBytes() != null) {
            capacities.get(2).setUsed(metrics.diskBytes());
            capacities.get(2).setPercent(metrics.diskCapacityBytes() == null || metrics.diskCapacityBytes() == 0
                    ? null : metrics.diskBytes() * 100D / metrics.diskCapacityBytes());
            if (metrics.diskCapacityBytes() != null) {
                capacities.get(2).setTotal(metrics.diskCapacityBytes());
            }
        }
    }

    private long rangeSeconds(String range) {
        return switch (range) {
            case "1h" -> 3600;
            case "6h" -> 21600;
            default -> 86400;
        };
    }

    private Double cpuPercent(Double cores, long millis) {
        return cores == null || millis == 0 ? null : cores * 100000D / millis;
    }

    private Double percent(Double value, long total) {
        return value == null || total == 0 ? null : value * 100D / total;
    }

    private long cpuMillis(String value) {
        try {
            return value.endsWith("m") ? Long.parseLong(value.substring(0, value.length() - 1))
                    : Math.round(Double.parseDouble(value) * 1000);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private long bytes(String value) {
        try {
            String[] units = { "Ki", "Mi", "Gi", "Ti", "Pi" };
            for (int index = 0; index < units.length; index++) {
                if (value.endsWith(units[index])) {
                    return Math.round(Double.parseDouble(value.substring(0, value.length() - 2)) * (1L << (10 * (index + 1))));
                }
            }
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private int nodePodCapacity(K8sNode node) {
        if (node.getStatus() == null || node.getStatus().getAllocatable() == null) {
            return 0;
        }
        try {
            return Integer.parseInt(node.getStatus().getAllocatable().getOrDefault("pods", "0"));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

}
