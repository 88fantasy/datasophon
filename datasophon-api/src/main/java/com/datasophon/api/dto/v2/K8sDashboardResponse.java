package com.datasophon.api.dto.v2;

import java.time.Instant;
import java.util.List;

import lombok.Builder;
import lombok.Data;

/** K8s 集群监控概览的聚合响应。 */
@Data
@Builder
public class K8sDashboardResponse {
    private Instant observedAt;
    private Telemetry telemetry;
    private Overview overview;
    private List<Capacity> capacities;
    private List<TrendPoint> trends;
    private List<Namespace> namespaces;
    private List<Workload> workloads;
    private List<Node> nodes;
    private List<Event> events;

    @Data
    @Builder
    public static class Telemetry {
        private String status;
        private String message;
    }

    @Data
    @Builder
    public static class Overview {
        private String health;
        private int readyNodes;
        private int totalNodes;
        private int runningPods;
        private int totalPods;
        private int critical;
        private int warning;
    }

    @Data
    @Builder
    public static class Node {
        private String name;
        private String status;
        private Integer podCount;
        private Integer podCapacity;
        private Double cpuPercent;
        private Double memoryPercent;
        private Double diskPercent;
    }

    @Data
    @Builder
    public static class Capacity {
        private String name;
        private Double percent;
        private Double used;
        private Double total;
        private String unit;
    }

    @Data
    @Builder
    public static class TrendPoint {
        private Instant timestamp;
        private Double cpuPercent;
        private Double memoryPercent;
        private Double networkMbps;
    }

    @Data
    @Builder
    public static class Namespace {
        private String name;
        private Integer podCount;
        private Double cpuCores;
        private Double memoryBytes;
    }

    @Data
    @Builder
    public static class Workload {
        private String name;
        private String namespace;
        private String type;
        private Integer ready;
        private Integer desired;
        private String status;
    }

    @Data
    @Builder
    public static class Event {
        private String type;
        private String reason;
        private String namespace;
        private String object;
        private String message;
        private String lastTimestamp;
    }
}
