package com.datasophon.api.vo.k8s;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * K8s Deployment 资源信息
 *
 * @author zhanghuangbin
 */
@Data
public class K8sDeploymentInfo {

    @Schema(description = "Deployment 名称")
    private String name;

    @Schema(description = "命名空间")
    private String namespace;

    @Schema(description = "运行时间")
    private String age;

    @Schema(description = "就绪副本数")
    private Integer readyReplicas;

    @Schema(description = "期望副本数")
    private Integer replicas;

    @Schema(description = "可用副本数")
    private Integer availableReplicas;

    @Schema(description = "未就绪副本数")
    private Integer unavailableReplicas;

    @Schema(description = "状态（Ready/Progressing/Failed）")
    private String status;

    @Schema(description = "镜像列表")
    private List<String> images;

    @Schema(description = "选择器标签")
    private String selector;

    @Schema(description = "更新策略")
    private String strategy;

    @Schema(description = "标签")
    private Map<String, String> labels;

}
