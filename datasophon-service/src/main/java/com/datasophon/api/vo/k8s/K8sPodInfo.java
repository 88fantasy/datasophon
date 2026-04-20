package com.datasophon.api.vo.k8s;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * K8s Pod 资源信息
 *
 * @author zhanghuangbin
 */
@Data
public class K8sPodInfo {

    @Schema(description = "Pod 名称")
    private String name;

    @Schema(description = "命名空间")
    private String namespace;

    @Schema(description = "Pod 状态（Pending/Running/Succeeded/Failed/Unknown）")
    private String status;

    @Schema(description = "运行时间")
    private String age;

    @Schema(description = "就绪/期望副本数")
    private String ready;

    @Schema(description = "重启次数")
    private Integer restartCount;

    @Schema(description = "节点名称")
    private String nodeName;

    @Schema(description = "Pod IP")
    private String podIP;

    @Schema(description = "标签")
    private Map<String, String> labels;

    @Schema(description = "容器名称")
    private List<String> containerNames;


}
