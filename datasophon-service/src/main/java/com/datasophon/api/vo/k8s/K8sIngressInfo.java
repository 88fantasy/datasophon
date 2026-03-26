package com.datasophon.api.vo.k8s;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * K8s Ingress 资源信息
 *
 * @author zhanghuangbin
 */
@Data
public class K8sIngressInfo {

    @Schema(description = "Ingress 名称")
    private String name;

    @Schema(description = "命名空间")
    private String namespace;

    @Schema(description = "运行时间")
    private String age;

    @Schema(description = "Ingress 类")
    private String ingressClass;

    @Schema(description = "主机名")
    private List<String> hosts;

    @Schema(description = "负载均衡地址")
    private String loadBalancerAddress;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "标签")
    private Map<String, String> labels;

}
