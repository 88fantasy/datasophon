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

package com.datasophon.api.dto.v2;

import com.datasophon.api.service.host.dto.QueryHostListPageDTO;
import com.datasophon.common.k8s.vo.k8s.K8sNode;
import com.datasophon.dao.entity.ClusterHostDO;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Data;

/**
 * 主机详情响应体。
 *
 * <p>安全说明：故意排除 {@code sshPassword}、{@code sshUser}、{@code managed} 字段，
 * 避免敏感信息泄漏。{@code hostState} 使用 {@link Integer}（值 1/2/3），
 * 而非枚举，以匹配前端 {@code HOST_STATE_MAP} 的数字 key。
 */
@Data
public class HostResponse {

    private Integer id;
    private String hostname;
    private String ip;
    private String rack;
    private Integer coreNum;
    private Integer totalMem;
    private Integer totalDisk;
    private Integer usedMem;
    private Integer usedDisk;
    private String averageLoad;
    private Date checkTime;
    private Date createTime;
    private Integer clusterId;
    /** 主机状态：1=正常运行，2=掉线，3=存在告警。使用 Integer 而非枚举，匹配前端数字 key。 */
    private Integer hostState;
    private String cpuArchitecture;
    private String nodeLabel;
    private Integer serviceRoleNum;
    private Integer sshPort;
    // sshUser、sshPassword、managed — 安全原因不暴露

    /**
     * 从 {@link ClusterHostDO} 实体构建响应体。
     * <p>hostState 取枚举的 {@code getValue()}（整数），避免 {@code @JsonValue} 序列化为中文。
     */
    public static HostResponse from(ClusterHostDO entity) {
        HostResponse r = new HostResponse();
        r.setId(entity.getId());
        r.setHostname(entity.getHostname());
        r.setIp(entity.getIp());
        r.setRack(entity.getRack());
        r.setCoreNum(entity.getCoreNum());
        r.setTotalMem(entity.getTotalMem());
        r.setTotalDisk(entity.getTotalDisk());
        r.setUsedMem(entity.getUsedMem());
        r.setUsedDisk(entity.getUsedDisk());
        r.setAverageLoad(entity.getAverageLoad());
        r.setCheckTime(entity.getCheckTime());
        r.setCreateTime(entity.getCreateTime());
        r.setClusterId(entity.getClusterId());
        r.setHostState(entity.getHostState() != null ? entity.getHostState().getValue() : null);
        r.setCpuArchitecture(entity.getCpuArchitecture());
        r.setNodeLabel(entity.getNodeLabel());
        r.setServiceRoleNum(entity.getServiceRoleNum());
        r.setSshPort(entity.getSshPort());
        return r;
    }

    /**
     * 从 service 层内部 DTO {@link QueryHostListPageDTO} 构建响应体。
     * <p>listByPage 返回的是已处理好的 QueryHostListPageDTO（hostState 已是 Integer），
     * 直接映射即可。
     */
    public static HostResponse fromPageDto(QueryHostListPageDTO dto) {
        HostResponse r = new HostResponse();
        r.setId(dto.getId());
        r.setHostname(dto.getHostname());
        r.setIp(dto.getIp());
        r.setRack(dto.getRack());
        r.setCoreNum(dto.getCoreNum());
        r.setTotalMem(dto.getTotalMem());
        r.setTotalDisk(dto.getTotalDisk());
        r.setUsedMem(dto.getUsedMem());
        r.setUsedDisk(dto.getUsedDisk());
        r.setAverageLoad(dto.getAverageLoad());
        r.setCheckTime(dto.getCheckTime());
        r.setCreateTime(dto.getCreateTime());
        r.setClusterId(dto.getClusterId());
        r.setHostState(dto.getHostState());
        r.setCpuArchitecture(dto.getCpuArchitecture());
        r.setNodeLabel(dto.getNodeLabel());
        r.setServiceRoleNum(dto.getServiceRoleNum());
        return r;
    }

    public static List<HostResponse> fromList(List<ClusterHostDO> entities) {
        return entities.stream().map(HostResponse::from).collect(Collectors.toList());
    }

    public static List<HostResponse> fromPageDtoList(List<QueryHostListPageDTO> dtos) {
        return dtos.stream().map(HostResponse::fromPageDto).collect(Collectors.toList());
    }

    public static HostResponse fromK8sNode(Integer clusterId, K8sNode node) {
        HostResponse r = new HostResponse();
        K8sNode.Metadata metadata = node.getMetadata();
        K8sNode.NodeStatus status = node.getStatus();
        String name = metadata != null ? metadata.getName() : null;
        r.setId(stableNodeId(name));
        r.setHostname(name);
        r.setClusterId(clusterId);
        r.setIp(findAddress(status, "InternalIP"));
        r.setHostState(isReady(status) ? 1 : 2);
        r.setCreateTime(parseDate(metadata != null ? metadata.getCreationTimestamp() : null));
        r.setCheckTime(findReadyHeartbeatTime(status));
        r.setCoreNum(parseCpu(status != null ? status.getCapacity() : null));
        r.setTotalMem(parseStorageGb(status != null ? status.getCapacity() : null, "memory"));
        r.setTotalDisk(parseStorageGb(status != null ? status.getCapacity() : null, "ephemeral-storage"));
        r.setUsedMem(0);
        r.setUsedDisk(0);
        r.setAverageLoad("-");
        r.setCpuArchitecture(findArchitecture(status));
        r.setNodeLabel(extractNodeRole(metadata != null ? metadata.getLabels() : null));
        r.setServiceRoleNum(0);
        return r;
    }

    private static Integer stableNodeId(String name) {
        if (name == null) {
            return 0;
        }
        int hash = name.hashCode();
        return hash == Integer.MIN_VALUE ? 0 : Math.abs(hash);
    }

    private static String findAddress(K8sNode.NodeStatus status, String type) {
        if (status == null || status.getAddresses() == null) {
            return null;
        }
        return status.getAddresses().stream()
                .filter(address -> type.equals(address.getType()))
                .map(K8sNode.NodeAddress::getAddress)
                .findFirst()
                .orElse(null);
    }

    private static boolean isReady(K8sNode.NodeStatus status) {
        if (status == null || status.getConditions() == null) {
            return false;
        }
        return status.getConditions().stream()
                .anyMatch(condition -> "Ready".equals(condition.getType()) && "True".equals(condition.getStatus()));
    }

    private static Date findReadyHeartbeatTime(K8sNode.NodeStatus status) {
        if (status == null || status.getConditions() == null) {
            return null;
        }
        return status.getConditions().stream()
                .filter(condition -> "Ready".equals(condition.getType()))
                .map(K8sNode.NodeCondition::getLastHeartbeatTime)
                .filter(value -> value != null && !value.isBlank())
                .map(HostResponse::parseDate)
                .findFirst()
                .orElse(null);
    }

    private static Integer parseCpu(Map<String, String> capacity) {
        if (capacity == null) {
            return null;
        }
        String cpu = capacity.get("cpu");
        if (cpu == null || cpu.isBlank()) {
            return null;
        }
        try {
            if (cpu.endsWith("m")) {
                double milliCpu = Double.parseDouble(cpu.substring(0, cpu.length() - 1));
                return (int) Math.ceil(milliCpu / 1000D);
            }
            return (int) Math.ceil(Double.parseDouble(cpu));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer parseStorageGb(Map<String, String> capacity, String key) {
        if (capacity == null) {
            return null;
        }
        String value = capacity.get(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            double bytes;
            if (value.endsWith("Ki")) {
                bytes = Double.parseDouble(value.substring(0, value.length() - 2)) * 1024D;
            } else if (value.endsWith("Mi")) {
                bytes = Double.parseDouble(value.substring(0, value.length() - 2)) * 1024D * 1024D;
            } else if (value.endsWith("Gi")) {
                bytes = Double.parseDouble(value.substring(0, value.length() - 2)) * 1024D * 1024D * 1024D;
            } else {
                bytes = Double.parseDouble(value);
            }
            return (int) Math.round(bytes / 1024D / 1024D / 1024D);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String findArchitecture(K8sNode.NodeStatus status) {
        if (status == null || status.getNodeInfo() == null) {
            return null;
        }
        return status.getNodeInfo().getArchitecture();
    }

    private static String extractNodeRole(Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) {
            return null;
        }
        return labels.keySet().stream()
                .filter(key -> key.startsWith("node-role.kubernetes.io/"))
                .map(key -> key.substring("node-role.kubernetes.io/".length()))
                .map(role -> role.isEmpty() ? "master" : role)
                .collect(Collectors.joining(","));
    }

    private static Date parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Date.from(Instant.parse(value));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
