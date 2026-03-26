package com.datasophon.dao.vo.instance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class K8sServiceInstanceVO {


    private Integer id;

    @Schema(description = "集群")
    private Integer clusterId;

    @Schema(description = "名空间ID")
    private Integer namespaceId;

    @Schema(description = "名空间")
    private String namespace;

    @Schema(description = "服务ID")
    private Integer serviceId;

    @Schema(description = "服务名称")
    private String serviceName;

    @Schema(description = "0初始化 1成功 2失败")
    private Integer state;
}
