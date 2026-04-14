package com.datasophon.api.vo.k8s;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * K8s Event 资源信息
 *
 * @author zhanghuangbin
 */
@Data
public class K8sEventInfo {

    @Schema(description = "事件名称")
    private String name;

    @Schema(description = "命名空间")
    private String namespace;

    @Schema(description = "事件类型（Normal/Warning）")
    private String type;

    @Schema(description = "事件原因")
    private String reason;

    @Schema(description = "事件消息")
    private String message;

    @Schema(description = "首次发生时间")
    private String firstTimestamp;

    @Schema(description = "最后发生时间")
    private String lastTimestamp;

    @Schema(description = "事件发生次数")
    private Integer count;

    @Schema(description = "关联对象类型")
    private String involvedObjectKind;

    @Schema(description = "关联对象名称")
    private String involvedObjectName;

    @Schema(description = "关联对象命名空间")
    private String involvedObjectNamespace;

    @Schema(description = "事件来源组件")
    private String sourceComponent;

    @Schema(description = "事件来源主机")
    private String sourceHost;

    @Schema(description = "报告组件")
    private String reportingComponent;

    @Schema(description = "运行时间（从首次发生时间计算）")
    private String age;

}
