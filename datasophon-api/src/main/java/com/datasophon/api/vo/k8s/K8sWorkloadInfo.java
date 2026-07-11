package com.datasophon.api.vo.k8s;

import lombok.Data;

/** K8s 工作负载的简要运行状态。 */
@Data
public class K8sWorkloadInfo {
    private String name;
    private String namespace;
    private String type;
    private Integer ready;
    private Integer desired;
}
