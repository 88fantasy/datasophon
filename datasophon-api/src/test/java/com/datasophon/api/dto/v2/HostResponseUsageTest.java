package com.datasophon.api.dto.v2;

import static org.assertj.core.api.Assertions.assertThat;

import com.datasophon.api.service.k8s.K8sDashboardMetricsService.HostUsage;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * K8s 主机列表的用量补齐。
 *
 * <p>K8s 的 node 对象只有 capacity，没有用量；用量来自 OTel 节点指标。补不上时必须保持降级值，
 * 不能让主机列表因为指标查不到而报错——这是本类用例的核心约束。
 */
class HostResponseUsageTest {

    private static final double GB = 1024d * 1024 * 1024;

    @Test
    @DisplayName("按主机名匹配到指标时换算成 GB 并写入用量")
    void appliesUsageMatchedByHostname() {
        HostResponse host = k8sHost("192.168.201.19", "192.168.201.19");

        host.applyUsage(Map.of("192.168.201.19", new HostUsage(60.2 * GB, 37.6 * GB, 5.46)));

        assertThat(host.getUsedMem()).isEqualTo(60);
        assertThat(host.getUsedDisk()).isEqualTo(38);
        assertThat(host.getAverageLoad()).isEqualTo("5.46");
    }

    @Test
    @DisplayName("主机名对不上时回落用 IP 匹配")
    void fallsBackToIpMatch() {
        HostResponse host = k8sHost("node-19", "192.168.201.19");

        host.applyUsage(Map.of("192.168.201.19", new HostUsage(8 * GB, null, null)));

        assertThat(host.getUsedMem()).isEqualTo(8);
    }

    @Test
    @DisplayName("没有采集到的节点保持降级值，不置空也不报错")
    void keepsFallbackWhenNodeHasNoMetrics() {
        // 真实场景：control-plane 未配 toleration，采集 DaemonSet 调度不上去
        HostResponse host = k8sHost("192.168.201.35", "192.168.201.35");

        host.applyUsage(Map.of("192.168.201.19", new HostUsage(60 * GB, 37 * GB, 5.46)));

        assertThat(host.getUsedMem()).isZero();
        assertThat(host.getUsedDisk()).isZero();
        assertThat(host.getAverageLoad()).isEqualTo("-");
    }

    @Test
    @DisplayName("整体查询失败（空 Map / null）时同样保持降级值")
    void keepsFallbackWhenQueryFailed() {
        HostResponse empty = k8sHost("192.168.201.19", "192.168.201.19");
        empty.applyUsage(Map.of());
        assertThat(empty.getUsedMem()).isZero();
        assertThat(empty.getAverageLoad()).isEqualTo("-");

        HostResponse nulled = k8sHost("192.168.201.19", "192.168.201.19");
        nulled.applyUsage(null);
        assertThat(nulled.getAverageLoad()).isEqualTo("-");
    }

    @Test
    @DisplayName("节点名与 IP 不同时，memory/disk 落在节点名一条记录、load 落在 IP 另一条记录，两条需要合并生效")
    void mergesHostnameAndIpKeyedRecordsWhenNodeNameDiffersFromIp() {
        // 复现 C3：memory/disk 按 k8s.node.name 归键，load 按 service_instance_id（IP）归键，
        // 节点名 != IP 时两者是 usage 里的两条不同记录；命中 hostname 那条（load 为 null）后
        // 若不回落 ip 那条，averageLoad 会恒为 "-"。
        HostResponse host = k8sHost("node-19", "192.168.201.19");

        host.applyUsage(Map.of(
                "node-19", new HostUsage(60.2 * GB, 37.6 * GB, null),
                "192.168.201.19", new HostUsage(null, null, 5.46)));

        assertThat(host.getUsedMem()).isEqualTo(60);
        assertThat(host.getUsedDisk()).isEqualTo(38);
        assertThat(host.getAverageLoad()).isEqualTo("5.46");
    }

    @Test
    @DisplayName("指标部分缺失时只覆盖有值的字段")
    void appliesOnlyPresentMetrics() {
        // 负载来自 hostmetrics receiver，与内存/磁盘不同源，可能单独缺失
        HostResponse host = k8sHost("192.168.201.19", "192.168.201.19");

        host.applyUsage(Map.of("192.168.201.19", new HostUsage(60 * GB, null, null)));

        assertThat(host.getUsedMem()).isEqualTo(60);
        assertThat(host.getUsedDisk()).isZero();
        assertThat(host.getAverageLoad()).isEqualTo("-");
    }

    /** 复刻 fromK8sNode 产出的初始状态：有容量、无用量。 */
    private static HostResponse k8sHost(String hostname, String ip) {
        HostResponse host = new HostResponse();
        host.setHostname(hostname);
        host.setIp(ip);
        host.setTotalMem(126);
        host.setTotalDisk(44);
        host.setUsedMem(0);
        host.setUsedDisk(0);
        host.setAverageLoad("-");
        return host;
    }
}
